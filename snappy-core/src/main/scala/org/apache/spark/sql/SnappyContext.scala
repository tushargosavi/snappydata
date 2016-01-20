/*
 * Copyright (c) 2016 SnappyData, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package org.apache.spark.sql

import java.sql.{Connection, SQLException}

import scala.collection.mutable
import scala.language.implicitConversions
import scala.reflect.runtime.{universe => u}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import io.snappydata.{Constant, Property}

import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.aqp.{SnappyContextDefaultFunctions, SnappyContextFunctions}
import org.apache.spark.sql.catalyst.analysis.Analyzer
import org.apache.spark.sql.catalyst.plans.logical.{InsertIntoTable, LogicalPlan}
import org.apache.spark.sql.catalyst.rules.Rule
import org.apache.spark.sql.catalyst.{CatalystTypeConverters, InternalRow, ParserDialect}
import org.apache.spark.sql.collection.{ToolsCallbackInit, Utils}
import org.apache.spark.sql.columnar.{CachedBatch, ExternalStoreUtils, InMemoryAppendableRelation}
import org.apache.spark.sql.execution.ConnectionPool
import org.apache.spark.sql.execution.datasources.{LogicalRelation, PreInsertCastAndRename, ResolvedDataSource}
import org.apache.spark.sql.execution.ui.SQLListener
import org.apache.spark.sql.hive.{QualifiedTableName, SnappyStoreHiveCatalog}
import org.apache.spark.sql.jdbc.JdbcDialects
import org.apache.spark.sql.row.GemFireXDDialect
import org.apache.spark.sql.snappy.RDDExtensions
import org.apache.spark.sql.sources._
import org.apache.spark.sql.streaming._
import org.apache.spark.sql.types.StructType
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.Time
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.{Logging, SparkContext, SparkException}

/**
 * Main entry point for SnappyData extensions to Spark. A SnappyContext
 * extends Spark's [[org.apache.spark.sql.SQLContext]] to work with Row and
 * Column tables. Any DataFrame can be managed as SnappyData tables and any
 * table can be accessed as a DataFrame. This is similar to
 * [[org.apache.spark.sql.hive.HiveContext HiveContext]] - integrates the
 * SQLContext functionality with the Snappy store.
 *
 * When running in the '''embedded ''' mode (i.e. Spark executor collocated
 * with Snappy data store), Applications typically submit Jobs to the
 * Snappy-JobServer
 * (provide link) and do not explicitly create a SnappyContext. A single
 * shared context managed by SnappyData makes it possible to re-use Executors
 * across client connections or applications.
 *
 * @see document describing the various URL options to create
 *      Snappy/Spark Context
 * @see document describing the Job server API
 * @todo Provide links to above descriptions
 *
 */
class SnappyContext protected[spark](@transient override val sparkContext: SparkContext,
    override val listener: SQLListener,
    override val isRootContext: Boolean ,
    val snappyContextFunctions: SnappyContextFunctions =
                 GlobalSnappyInit.getSnappyContextFunctionsImpl)
    extends SQLContext(sparkContext, snappyContextFunctions.getSnappyCacheManager,
      listener, isRootContext)
    with Serializable with Logging {

  self =>
  protected[spark] def this(sc: SparkContext) {
    this(sc, SQLContext.createListenerAndUI(sc), true)
  }

  @transient protected[sql] val snappyCacheManager: SnappyCacheManager =
    cacheManager.asInstanceOf[SnappyCacheManager]

  // initialize GemFireXDDialect so that it gets registered

  GemFireXDDialect.init()
  GlobalSnappyInit.initGlobalSnappyContext(sparkContext)

  protected[sql] override lazy val conf: SQLConf = new SQLConf {
    override def caseSensitiveAnalysis: Boolean =
      getConf(SQLConf.CASE_SENSITIVE, false)

    override def unsafeEnabled: Boolean = if(snappyContextFunctions.isTungstenEnabled) {
      super.unsafeEnabled
    }else {
      false
    }
  }

  override def newSession(): SnappyContext = {
    new SnappyContext(this.sparkContext, this.listener, false, this.snappyContextFunctions)
  }

  @transient
  override protected[sql] val ddlParser =
    snappyContextFunctions.getSnappyDDLParser(self, sqlParser.parse)

  protected[sql] override def getSQLDialect(): ParserDialect = {
    if (conf.dialect == "sql") {
      snappyContextFunctions.getSQLDialect(self)
    } else {
      super.getSQLDialect()
    }
  }

  override protected[sql] def executePlan(plan: LogicalPlan) =
    snappyContextFunctions.executePlan(this, plan)

  @transient
  override lazy val catalog = this.snappyContextFunctions.getSnappyCatalog(this)

  /**
   * :: DeveloperApi ::
   * @todo do we need this anymore? If useful functionality, make this
   *       private to sql package ... SchemaDStream should use the data source
   *       API?
   *              Tagging as developer API, for now
   * @param stream
   * @param aqpTables
   * @param transformer
   * @param v
   * @tparam T
   * @return
   */
  @DeveloperApi
  def saveStream[T](stream: DStream[T],
      aqpTables: Seq[String],
      transformer: Option[(RDD[T]) => RDD[Row]])(implicit v: u.TypeTag[T]) {
    val transform = transformer match {
      case Some(x) => x
      case None => if (!(v.tpe =:= u.typeOf[Row])) {
        //check if the stream type is already a Row
        throw new IllegalStateException(" Transformation to Row type needs to be supplied")
      } else {
        null
      }
    }
    stream.foreachRDD((rdd: RDD[T], time: Time) => {

      val rddRows = if( transform != null) {
        transform(rdd)
      }else {
        rdd.asInstanceOf[RDD[Row]]
      }
      snappyContextFunctions.collectSamples(this, rddRows, aqpTables, time.milliseconds)
    })
  }

  /**
   * Append dataframe to cache table in Spark.
   * @todo should this be renamed to appendToTempTable(...) ?
   *
   * @param df
   * @param table
   * @param storageLevel default storage level is MEMORY_AND_DISK
   * @return  @todo -> return type?
   */
  def appendToCache(df: DataFrame, table: String,
                    storageLevel: StorageLevel = StorageLevel.MEMORY_AND_DISK) = {
    val useCompression = conf.useCompression
    val columnBatchSize = conf.columnBatchSize

    val tableIdent = catalog.newQualifiedTableName(table)
    val plan = catalog.lookupRelation(tableIdent, None)
    val relation = cacheManager.lookupCachedData(plan).getOrElse {
      cacheManager.cacheQuery(DataFrame(self, plan),
        Some(tableIdent.table), storageLevel)

      cacheManager.lookupCachedData(plan).getOrElse {
        sys.error(s"couldn't cache table $tableIdent")
      }
    }

    val (schema, output) = (df.schema, df.logicalPlan.output)

    val cached = df.rdd.mapPartitionsPreserve { rowIterator =>

      val batches = InMemoryAppendableRelation(useCompression, columnBatchSize,
        tableIdent, schema, relation.cachedRepresentation, output)

      val converter = CatalystTypeConverters.createToCatalystConverter(schema)

      rowIterator.map(converter(_).asInstanceOf[InternalRow])
        .foreach(batches.appendRow((), _))
      batches.forceEndOfBatch().iterator
    }.persist(storageLevel)

    // trigger an Action to materialize 'cached' batch
    if (cached.count() > 0) {
      relation.cachedRepresentation.asInstanceOf[InMemoryAppendableRelation]
          .appendBatch(cached.asInstanceOf[RDD[CachedBatch]])
    }
  }

  /**
   * @param rdd
   * @param table
   * @param schema
   * @param storageLevel
   */
  private[sql] def appendToCacheRDD(rdd: RDD[_], table: String, schema:
    StructType,
      storageLevel: StorageLevel = StorageLevel.MEMORY_AND_DISK) {
    val useCompression = conf.useCompression
    val columnBatchSize = conf.columnBatchSize

    val tableIdent = catalog.newQualifiedTableName(table)
    val plan = catalog.lookupRelation(tableIdent, None)
    val relation = cacheManager.lookupCachedData(plan).getOrElse {
      cacheManager.cacheQuery(DataFrame(this, plan),
        Some(tableIdent.table), storageLevel)

      cacheManager.lookupCachedData(plan).getOrElse {
        sys.error(s"couldn't cache table $tableIdent")
      }
    }

    val cached = rdd.mapPartitionsPreserve { rowIterator =>

      val batches = InMemoryAppendableRelation(useCompression, columnBatchSize,
        tableIdent, schema, relation.cachedRepresentation , schema.toAttributes)
      val converter = CatalystTypeConverters.createToCatalystConverter(schema)
      rowIterator.map(converter(_).asInstanceOf[InternalRow])
          .foreach(batches.appendRow((), _))
      batches.forceEndOfBatch().iterator

    }.persist(storageLevel)

    // trigger an Action to materialize 'cached' batch
    if (cached.count() > 0) {
      relation.cachedRepresentation.asInstanceOf[InMemoryAppendableRelation]
          .appendBatch(cached.asInstanceOf[RDD[CachedBatch]])
    }
  }

  /**
   * Empties the contents of the table without deleting the catalog entry.
   * @param tableName full table name to be truncated
   */
  def truncateTable(tableName: String): Unit =
    truncateTable(catalog.newQualifiedTableName(tableName))

  /**
   * Empties the contents of the table without deleting the catalog entry.
   * @param tableIdent qualified name of table to be truncated
   */
  private[sql] def truncateTable(tableIdent: QualifiedTableName): Unit = {
    val plan = catalog.lookupRelation(tableIdent, None)
    snappy.unwrapSubquery(plan) match {
      case LogicalRelation(br, _) =>
        cacheManager.tryUncacheQuery(DataFrame(self, plan))
        br match {
          case d: DestroyRelation => d.truncate()
        }
      case _ => cacheManager.lookupCachedData(plan).foreach(
        _.cachedRepresentation.asInstanceOf[InMemoryAppendableRelation]
            .truncate())
    }
  }


  /**
   * Create a stratified sample table.
   * @todo provide lot more details and examples to explain creating and
   *       using sample tables with time series and otherwise
   * @param tableName the qualified name of the table
   * @param schema
   * @param samplingOptions
   * @param ifExists
   */
  def createSampleTable(tableName: String, schema: Option[StructType],
      samplingOptions: Map[String, String],
      ifExists: Boolean = false): DataFrame = {
    val plan = createTable(catalog.newQualifiedTableName(tableName),
      SnappyContext.SAMPLE_SOURCE, schema.map(catalog.normalizeSchema),
      schemaDDL = None, SaveMode.ErrorIfExists, samplingOptions)
    DataFrame(self, plan)
  }

  /**
   * Create approximate structure to query top-K with time series support.
   * @todo provide lot more details and examples to explain creating and
   *       using TopK with time series
   * @param topKName the qualified name of the top-K structure
   * @param keyColumnName
   * @param inputDataSchema
   * @param topkOptions
   * @param ifExists
   */
  def createApproxTSTopK(topKName: String, keyColumnName: String,
      inputDataSchema: Option[StructType], topkOptions: Map[String, String],
      ifExists: Boolean = false): DataFrame = {
    val plan = createTable(catalog.newQualifiedTableName(topKName),
      SnappyContext.TOPK_SOURCE, inputDataSchema.map(catalog.normalizeSchema),
      schemaDDL = None, SaveMode.ErrorIfExists,
      topkOptions + ("key" -> keyColumnName))
    DataFrame(self, plan)
  }

  /**
   * Creates a SnappyData table. This differs from
   * SnappyContext.createExternalTable in that this API creates a persistent
   * catalog entry for the table which is recovered after restart.
   *
   * @param tableName Name of the table
   * @param provider  Provider name such as 'COLUMN', 'ROW', 'JDBC' etc.
   * @param options Properties for table creation
   * @return DataFrame for the table
   */
  def createTable(
      tableName: String,
      provider: String,
      options: Map[String, String]): DataFrame = {
    val plan = createTable(catalog.newQualifiedTableName(tableName), provider,
      userSpecifiedSchema = None, schemaDDL = None,
      SaveMode.ErrorIfExists, options)
    DataFrame(self, plan)
  }

  /**
   * Creates a SnappyData table. This differs from
   * SnappyContext.createExternalTable in that this API creates a persistent
   * catalog entry for the table which is recovered after restart.
   *
   * @param tableName Name of the table
   * @param provider Provider name such as 'COLUMN', 'ROW', 'JDBC' etc.
   * @param schema   Table schema
   * @param options  Properties for table creation
   * @return DataFrame for the table
   */
  def createTable(
      tableName: String,
      provider: String,
      schema: StructType,
      options: Map[String, String]): DataFrame = {
    val plan = createTable(catalog.newQualifiedTableName(tableName), provider,
      Some(catalog.normalizeSchema(schema)), schemaDDL = None,
      SaveMode.ErrorIfExists, options)
    DataFrame(self, plan)
  }

  /**
   * Creates a SnappyData table. This differs from
   * SnappyContext.createExternalTable in that this API creates a persistent
   * catalog entry for the table which is recovered after restart.
   *
   * @param tableName Name of the table
   * @param provider  Provider name such as 'COLUMN', 'ROW', 'JDBC' etc.
   * @param schemaDDL Table schema as a string interpreted by provider
   * @param options   Properties for table creation
   * @return DataFrame for the table
   */
  def createTable(
      tableName: String,
      provider: String,
      schemaDDL: String,
      options: Map[String, String]): DataFrame = {
    var schemaStr = schemaDDL.trim
    if (schemaStr.charAt(0) != '(') {
      schemaStr = "(" + schemaStr + ")"
    }
    val plan = createTable(catalog.newQualifiedTableName(tableName), provider,
      userSpecifiedSchema = None, Some(schemaStr),
      SaveMode.ErrorIfExists, options)
    DataFrame(self, plan)
  }

  /**
   * @todo Jags: Recommend we change behavior so createExternal is used only to
   * create non-snappy managed tables. 'createTable' should create snappy
   * managed tables.
   */


  /**
    * Create a table with given options.
    */
  private[sql] def createTable(
      tableIdent: QualifiedTableName,
      provider: String,
      userSpecifiedSchema: Option[StructType],
      schemaDDL: Option[String],
      mode: SaveMode,
      options: Map[String, String]): LogicalPlan = {

    if (catalog.tableExists(tableIdent)) {
      mode match {
        case SaveMode.ErrorIfExists =>
          throw new AnalysisException(
            s"createTable: Table $tableIdent already exists.")
        case _ =>
          return catalog.lookupRelation(tableIdent, None)
      }
    }

    // add tableName in properties if not already present
    val dbtableProp = JdbcExtendedUtils.DBTABLE_PROPERTY
    val params = if (options.keysIterator.exists(_.equalsIgnoreCase(
      dbtableProp))) {
      options
    }
    else {
      options + (dbtableProp -> tableIdent.toString)
    }

    val source = SnappyContext.getProvider(provider)
    val resolved = schemaDDL match {
      case Some(schema) => JdbcExtendedUtils.externalResolvedDataSource(self,
        schema, source, mode, params)

      case None =>
        // add allowExisting in properties used by some implementations
        ResolvedDataSource(self, userSpecifiedSchema, Array.empty[String],
          source, params + (JdbcExtendedUtils.ALLOW_EXISTING_PROPERTY ->
              (mode != SaveMode.ErrorIfExists).toString))
    }

    val plan = LogicalRelation(resolved.relation)
    catalog.registerExternalTable(tableIdent, userSpecifiedSchema,
      Array.empty[String], source, params,
      catalog.getTableType(resolved.relation))
    plan
  }

  /**
    * Create an external table with given options.
    */
  private[sql] def createTable(
      tableIdent: QualifiedTableName,
      provider: String,
      partitionColumns: Array[String],
      mode: SaveMode,
      options: Map[String, String],
      query: LogicalPlan): LogicalPlan = {

    var data = DataFrame(self, query)
    if (catalog.tableExists(tableIdent)) {
      mode match {
        case SaveMode.ErrorIfExists =>
          throw new AnalysisException(s"Table $tableIdent already exists. " +
              "If using SQL CREATE TABLE, you need to use the " +
              s"APPEND or OVERWRITE mode, or drop $tableIdent first.")
        case SaveMode.Ignore => return catalog.lookupRelation(tableIdent, None)
        case _ =>
          // existing table schema could have nullable columns
          val schema = data.schema
          if (schema.exists(!_.nullable)) {
            data = internalCreateDataFrame(data.queryExecution.toRdd,
              schema.asNullable)
          }
      }
    }

    // add tableName in properties if not already present
    val dbtableProp = JdbcExtendedUtils.DBTABLE_PROPERTY
    val params = if (options.keysIterator.exists(_.equalsIgnoreCase(
      dbtableProp))) {
      options
    }
    else {
      options + (dbtableProp -> tableIdent.toString)
    }

    // this gives the provider..

    val source = SnappyContext.getProvider(provider)
    val resolved = ResolvedDataSource(self, source, partitionColumns,
      mode, params, data)

    if (catalog.tableExists(tableIdent) && mode == SaveMode.Overwrite) {
      // uncache the previous results and don't register again
      cacheManager.tryUncacheQuery(data)
    }
    else {
      catalog.registerExternalTable(tableIdent, Some(data.schema),
        partitionColumns, source, params, catalog.getTableType(resolved.relation))
    }
    LogicalRelation(resolved.relation)
  }

  /**
   * Drop a SnappyData table created by a call to SnappyContext.createTable
   * @param tableName table to be dropped
   * @param ifExists  attempt drop only if the table exists
   */
  def dropTable(tableName: String, ifExists: Boolean = false): Unit =
    dropTable(catalog.newQualifiedTableName(tableName), ifExists)

  /**
   * Drop a SnappyData table created by a call to SnappyContext.createTable
   * @param tableIdent table to be dropped
   * @param ifExists  attempt drop only if the table exists
   */
  private[sql] def dropTable(tableIdent: QualifiedTableName,
      ifExists: Boolean): Unit = {
    val plan = try {
      catalog.lookupRelation(tableIdent, None)
    } catch {
      case ae: AnalysisException =>
        if (ifExists) return else throw ae
      case NonFatal(_) =>
        // table loading may fail due to an initialization exception
        // in relation, so try to remove from hive catalog in any case
        try {
          catalog.unregisterExternalTable(tableIdent)
          return
        } catch {
          case NonFatal(e) =>
            if (ifExists) return
            else throw new AnalysisException(s"Table Not Found: $tableIdent", e)
        }
    }
    // additional cleanup for external tables, if required
    snappy.unwrapSubquery(plan) match {
      case LogicalRelation(br, _) =>
        cacheManager.tryUncacheQuery(DataFrame(self, plan))
        catalog.unregisterExternalTable(tableIdent)
        br match {
          case d: DestroyRelation => d.destroy(ifExists)
          case _ => // Do nothing
        }
      case _ =>
        // assume a temporary table
        cacheManager.tryUncacheQuery(DataFrame(self, plan))
        catalog.unregisterTable(tableIdent)
    }
  }

  def createIndexOnTable(tableName: String, sql: String): Unit =
    createIndexOnTable(catalog.newQualifiedTableName(tableName), sql)

  /**
   * Create Index on a SnappyData table (created by a call to createTable).
   * @todo how can the user invoke this? sql?
   */
  private[sql] def createIndexOnTable(tableIdent: QualifiedTableName,
      sql: String): Unit = {
    //println("create-index" + " tablename=" + tableName    + " ,sql=" + sql)

    if (!catalog.tableExists(tableIdent)) {
      throw new AnalysisException(
        s"$tableIdent is not an indexable table")
    }

    //println("qualifiedTable=" + qualifiedTable)
    snappy.unwrapSubquery(catalog.lookupRelation(tableIdent, None)) match {
      case LogicalRelation(i: IndexableRelation, _) =>
        i.createIndex(tableIdent.unquotedString, sql)
      case _ => throw new AnalysisException(
        s"$tableIdent is not an indexable table")
    }
  }

  /**
   * Drop Index of a SnappyData table (created by a call to createIndexOnTable).
   */
  def dropIndexOfTable(sql: String): Unit = {
    //println("drop-index" + " sql=" + sql)

    var conn: Connection = null
    try {
      val connProperties =
        ExternalStoreUtils.validateAndGetAllProps(sparkContext, new mutable.HashMap[String, String])
      conn = ExternalStoreUtils.getConnection(connProperties.url, connProperties.connProps,
        JdbcDialects.get(connProperties.url), Utils.isLoner(sparkContext))
      JdbcExtendedUtils.executeUpdate(sql, conn)
    } catch {
      case sqle: java.sql.SQLException =>
        if (sqle.getMessage.contains("No suitable driver found")) {
          throw new AnalysisException(s"${sqle.getMessage}\n" +
            "Ensure that the 'driver' option is set appropriately and " +
            "the driver jars available (--jars option in spark-submit).")
        } else {
          throw sqle
        }
    } finally {
      if (conn != null) {
        conn.close()
      }
    }
  }

  /**
   * :: DeveloperApi ::
   * Insert one or more [[org.apache.spark.sql.Row]] into an existing table
   * @todo provide an example : insert a DF using foreachPartition...
   *       {{{
   *         someDataFrame.foreachPartition (x => snappyContext.insert
   *            ("MyTable", x.toSeq)
   *         )
   *       }}}
   * @param tableName
   * @param rows
   * @return
   */
  @DeveloperApi
  def insert(tableName: String, rows: Row*): Int = {
    val plan = catalog.lookupRelation(tableName)
    snappy.unwrapSubquery(plan) match {
      case LogicalRelation(r: RowInsertableRelation, _) => r.insert(rows)
      case _ => throw new AnalysisException(
        s"$tableName is not a row insertable table")
    }
  }

  /**
   * :: DeveloperApi ::
   * Update all rows in table that match passed filter expression
   * @todo provide an example
   * @param tableName
   * @param filterExpr
   * @param newColumnValues  A single Row containing all updated column
   *                         values. They MUST match the updateColumn list
   *                         passed
   * @param updateColumns   List of all column names being updated
   * @return
   */
  @DeveloperApi
  def update(tableName: String, filterExpr: String, newColumnValues: Row,
      updateColumns: String*): Int = {
    val plan = catalog.lookupRelation(tableName)
    snappy.unwrapSubquery(plan) match {
      case LogicalRelation(u: UpdatableRelation, _) =>
        u.update(filterExpr, newColumnValues, updateColumns)
      case _ => throw new AnalysisException(
        s"$tableName is not an updatable table")
    }
  }

  /**
   * :: DeveloperApi ::
   * Delete all rows in table that match passed filter expression
   *
   * @param tableName
   * @param filterExpr
   * @return
   */
  @DeveloperApi
  def delete(tableName: String, filterExpr: String): Int = {
    val plan = catalog.lookupRelation(tableName)
    snappy.unwrapSubquery(plan) match {
      case LogicalRelation(d: DeletableRelation, _) => d.delete(filterExpr)
      case _ => throw new AnalysisException(
        s"$tableName is not a deletable table")
    }
  }

  // end of insert/update/delete operations

  @transient
  override protected[sql] lazy val analyzer: Analyzer =
    snappyContextFunctions.createAnalyzer(self)

  @transient
  override protected[sql] val planner = this.snappyContextFunctions.getPlanner(this)


  /**
    * Fetch the topK entries in the Approx TopK synopsis for the specified
   * time interval. See _createTopK_ for how to create this data structure
   * and associate this to a base table (i.e. the full data set). The time
   * interval specified here should not be less than the minimum time interval
   * used when creating the TopK synopsis.
   * @todo provide an example and explain the returned DataFrame. Key is the
   *       attribute stored but the value is a struct containing
   *       count_estimate, and lower, upper bounds? How many elements are
   *       returned if K is not specified?
    *
    * @param topKName - The topK structure that is to be queried.
    * @param startTime start time as string of the format "yyyy-mm-dd hh:mm:ss".
    *                  If passed as null, oldest interval is considered as the start interval.
    * @param endTime  end time as string of the format "yyyy-mm-dd hh:mm:ss".
    *                 If passed as null, newest interval is considered as the last interval.
    * @param k Optional. Number of elements to be queried.
    *          This is to be passed only for stream summary
    * @return returns the top K elements with their respective frequencies between two time
    */
  def queryApproxTSTopK(topKName: String,
      startTime: String = null, endTime: String = null,
      k: Int = -1): DataFrame =
      snappyContextFunctions.queryTopK(this, topKName,
        startTime, endTime, k)

  /**
   * @todo why do we need this method? K is optional in the above method
   */
  def queryApproxTSTopK(topKName: String,
      startTime: Long, endTime: Long): DataFrame =
    queryApproxTSTopK(topKName, startTime, endTime, -1)

  def queryApproxTSTopK(topK: String,
      startTime: Long, endTime: Long, k: Int): DataFrame =
    snappyContextFunctions.queryTopK(this, topK, startTime, endTime, k)
}

/**
 * @todo document me
 */
object GlobalSnappyInit {

  private val aqpContextFunctionImplClass =
    "org.apache.spark.sql.execution.SnappyContextAQPFunctions"

  private[spark] def getSnappyContextFunctionsImpl: SnappyContextFunctions = {
    Try {
      val mirror = u.runtimeMirror(getClass.getClassLoader)
      val cls = mirror.classSymbol(org.apache.spark.util.Utils.
          classForName(aqpContextFunctionImplClass))
      val clsType = cls.toType
      val classMirror = mirror.reflectClass(clsType.typeSymbol.asClass)
      val defaultCtor = clsType.member(u.nme.CONSTRUCTOR)
      val runtimeCtr = classMirror.reflectConstructor(defaultCtor.asMethod)
      runtimeCtr().asInstanceOf[SnappyContextFunctions]
    } match {
      case Success(v) => v
      case Failure(_) => SnappyContextDefaultFunctions
    }
  }

  @volatile private[this] var _globalSNContextInitialized: Boolean = false
  private[this] val contextLock = new AnyRef

  private[sql] def initGlobalSnappyContext(sc: SparkContext) = {
    if (!_globalSNContextInitialized ) {
      contextLock.synchronized {
        if (!_globalSNContextInitialized) {
          invokeServices(sc)
          _globalSNContextInitialized = true
        }
      }
    }
  }

  private[sql] def resetGlobalSNContext(): Unit =
    _globalSNContextInitialized = false

  private def invokeServices(sc: SparkContext): Unit = {
    SnappyContext.getClusterMode(sc) match {
      case SnappyEmbeddedMode(_, _) =>
        // NOTE: if Property.jobServer.enabled is true
        // this will trigger SnappyContext.apply() method
        // prior to `new SnappyContext(sc)` after this
        // method ends.
        ToolsCallbackInit.toolsCallback.invokeLeadStartAddonService(sc)
      case SnappyShellMode(_, _) =>
        ToolsCallbackInit.toolsCallback.invokeStartFabricServer(sc,
          hostData = false)
      case ExternalEmbeddedMode(_, url) =>
        SnappyContext.urlToConf(url, sc)
        ToolsCallbackInit.toolsCallback.invokeStartFabricServer(sc,
          hostData = false)
      case LocalMode(_, url) =>
        SnappyContext.urlToConf(url, sc)
        ToolsCallbackInit.toolsCallback.invokeStartFabricServer(sc,
          hostData = true)
      case _ => // ignore
    }
  }
}

object SnappyContext extends Logging {

  @volatile private[this] var _anySNContext: SnappyContext = _
  @volatile private[this] var _clusterMode: ClusterMode = _

  private[this] val contextLock = new AnyRef

  val DEFAULT_SOURCE = "row"
  val SAMPLE_SOURCE = "column_sample"
  val TOPK_SOURCE = "approx_topk"

  private val builtinSources = Map(
    "jdbc" -> classOf[row.DefaultSource].getCanonicalName,
    "column" -> classOf[columnar.DefaultSource].getCanonicalName,
    SAMPLE_SOURCE -> "org.apache.spark.sql.sampling.DefaultSource",
    TOPK_SOURCE -> "org.apache.spark.sql.topk.DefaultSource",
    "row" -> "org.apache.spark.sql.rowtable.DefaultSource",
    "socket_stream" -> classOf[SocketStreamSource].getCanonicalName,
    "file_stream" -> classOf[FileStreamSource].getCanonicalName,
    "kafka_stream" -> classOf[KafkaStreamSource].getCanonicalName,
    "directkafka_stream" -> classOf[DirectKafkaStreamSource].getCanonicalName,
    "twitter_stream" -> classOf[TwitterStreamSource].getCanonicalName
  )

  def globalSparkContext: SparkContext = SparkContext.activeContext.get()

  private def newSnappyContext(sc: SparkContext) = {
    val snc = new SnappyContext(sc)
    // No need to synchronize. any occurrence would do
    if (_anySNContext == null) {
      _anySNContext = snc
    }
    snc
  }

  /**
   * @todo document me
   * @return
   */
  def apply(): SnappyContext = {
    val gc = globalSparkContext
    if (gc != null) {
      newSnappyContext(gc)
    } else {
      null
    }
  }

  /**
   * @todo document me
   * @param sc
   * @return
   */
  def apply(sc: SparkContext): SnappyContext = {
    if (sc != null) {
      newSnappyContext(sc)
    } else {
      apply()
    }
  }

  /**
   * @todo document me
   * @param sc
   * @return
   */
  def getOrCreate(sc: SparkContext): SnappyContext = {
    val gnc = _anySNContext
    if (gnc != null) gnc
    else contextLock.synchronized {
      val gnc = _anySNContext
      if (gnc != null) gnc
      else {
        apply(sc)
      }
    }
  }


  /**
   * @todo document me
   * @param url
   * @param sc
   */
  def urlToConf(url: String, sc: SparkContext): Unit = {
    val propValues = url.split(';')
    propValues.foreach { s =>
      val propValue = s.split('=')
      // propValue should always give proper result since the string
      // is created internally by evalClusterMode
      sc.conf.set(Constant.STORE_PROPERTY_PREFIX + propValue(0),
        propValue(1))
    }
  }

  /**
   * @todo document me
   * @param sc
   * @return
   */
  def getClusterMode(sc: SparkContext): ClusterMode = {
    val mode = _clusterMode
    if ((mode != null && mode.sc == sc) || sc == null) {
      mode
    } else if (mode != null) {
      evalClusterMode(sc)
    } else contextLock.synchronized {
      val mode = _clusterMode
      if ((mode != null && mode.sc == sc) || sc == null) {
        mode
      } else if (mode != null) {
        evalClusterMode(sc)
      } else {
        _clusterMode = evalClusterMode(sc)
        _clusterMode
      }
    }
  }

  private def evalClusterMode(sc: SparkContext): ClusterMode = {
    if (sc.master.startsWith(Constant.JDBC_URL_PREFIX)) {
      if (ToolsCallbackInit.toolsCallback == null) {
        throw new SparkException("Missing 'io.snappydata.ToolsCallbackImpl$'" +
            " from SnappyData tools package")
      }
      SnappyEmbeddedMode(sc,
        sc.master.substring(Constant.JDBC_URL_PREFIX.length))
    } else if (ToolsCallbackInit.toolsCallback != null) {
      val conf = sc.conf
      val local = Utils.isLoner(sc)
      val embedded = conf.getOption(Property.embedded).exists(_.toBoolean)
      conf.getOption(Property.locators).collectFirst {
        case s if !s.isEmpty =>
          val url = "locators=" + s + ";mcast-port=0"
          if (local) LocalMode(sc, url)
          else if (embedded) ExternalEmbeddedMode(sc, url)
          else SnappyShellMode(sc, url)
      }.orElse(conf.getOption(Property.mcastPort).collectFirst {
        case s if s.toInt > 0 =>
          val url = "mcast-port=" + s
          if (local) LocalMode(sc, url)
          else if (embedded) ExternalEmbeddedMode(sc, url)
          else SnappyShellMode(sc, url)
      }).getOrElse {
        if (local) LocalMode(sc, "mcast-port=0")
        else ExternalClusterMode(sc, sc.master)
      }
    } else {
      ExternalClusterMode(sc, sc.master)
    }
  }

  /**
   * @todo document me
   */
  def stop(): Unit = {
    val sc = globalSparkContext
    if (sc != null && !sc.isStopped) {
      // clean up the connection pool on executors first
      Utils.mapExecutors(sc, { (tc, p) =>
        ConnectionPool.clear()
        Iterator.empty
      }).count()
      // then on the driver
      ConnectionPool.clear()
      // clear current hive catalog connection
      SnappyStoreHiveCatalog.closeCurrent()
      if (ExternalStoreUtils.isShellOrLocalMode(sc)) {
        try {
          ToolsCallbackInit.toolsCallback.invokeStopFabricServer(sc)
        } catch {
          case se: SQLException if se.getCause.getMessage.indexOf(
            "No connection to the distributed system") != -1 => // ignore
        }
      }
      sc.stop()
    }
    _clusterMode = null
    _anySNContext = null
    GlobalSnappyInit.resetGlobalSNContext()
  }

  /**
   * Checks if the passed provider is recognized
   * @param providerName
   * @return
   */
  def getProvider(providerName: String): String =
    builtinSources.getOrElse(providerName, providerName)
}

// end of SnappyContext

private[sql] object PreInsertCheckCastAndRename extends Rule[LogicalPlan] {
  def apply(plan: LogicalPlan): LogicalPlan = plan transform {
    // Wait until children are resolved.
    case p: LogicalPlan if !p.childrenResolved => p

    // Check for SchemaInsertableRelation first
    case i@InsertIntoTable(l@LogicalRelation(r:
        SchemaInsertableRelation, _), _, child, _, _) =>
      r.schemaForInsert(child.output) match {
        case Some(output) =>
          PreInsertCastAndRename.castAndRenameChildOutput(i, output, child)
        case None =>
          throw new AnalysisException(s"$l requires that the query in the " +
              "SELECT clause of the INSERT INTO/OVERWRITE statement " +
              "generates the same number of columns as its schema.")
      }

    // We are inserting into an InsertableRelation or HadoopFsRelation.
    case i@InsertIntoTable(l@LogicalRelation(_: InsertableRelation |
                                             _: HadoopFsRelation, _), _, child, _, _) =>
      // First, make sure the data to be inserted have the same number of fields with the
      // schema of the relation.
      if (l.output.size != child.output.size) {
        throw new AnalysisException(s"$l requires that the query in the " +
            "SELECT clause of the INSERT INTO/OVERWRITE statement " +
            "generates the same number of columns as its schema.")
      }
      PreInsertCastAndRename.castAndRenameChildOutput(i, l.output, child)
  }
}

abstract class ClusterMode {
  val sc: SparkContext
  val url: String
}

case class SnappyEmbeddedMode(override val sc: SparkContext,
    override val url: String) extends ClusterMode

case class SnappyShellMode(override val sc: SparkContext,
    override val url: String) extends ClusterMode

case class ExternalEmbeddedMode(override val sc: SparkContext,
    override val url: String) extends ClusterMode

case class LocalMode(override val sc: SparkContext,
    override val url: String) extends ClusterMode

case class ExternalClusterMode(override val sc: SparkContext,
    override val url: String) extends ClusterMode
