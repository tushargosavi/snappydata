io.snappydata.hydra.cluster.SnappyPrms-analyticalQueryList =
  " select avg(arrdelay), avg(depdelay) from airline group by flightnum"
  " select avg(arrdelay), avg(depdelay), uniquecarrier from airline group by uniquecarrier"
  " select avg(arrdelay), avg(depdelay), uniquecarrier, year_ from airline group by uniquecarrier, year_"
  " select avg(arrdelay) from airline"
  " select avg(arrdelay) from airline group by uniquecarrier"
  " select avg(arrdelay), max(arrdelay), avg(depdelay), max(depdelay), origin from airline group by origin order by avg(arrdelay) desc"
  " select avg(arrdelay), uniquecarrier from airline group by uniquecarrier"
  " select avg(arrdelay), uniquecarrier from airline where uniquecarrier = 'UA' group by uniquecarrier"
  " select avg(arrdelay), uniquecarrier, year_, month_ from airline group by uniquecarrier, year_, month_  order by avg(arrdelay) desc"
  " select avg(depdelay) avgdelay, avg(arrdelay), uniquecarrier from airline where uniquecarrier in ('UA', 'DL') group by uniquecarrier order by avgdelay desc"
  " select avg(depdelay) avgdelay, uniquecarrier from airline group by uniquecarrier order by avgdelay desc"
  " select avg(depdelay) avgdelay, uniquecarrier from airline where uniquecarrier in ('UA', 'DL') group by uniquecarrier order by avgdelay desc"
  " select avg(depdelay) avgdelay, uniquecarrier from airline where uniquecarrier = 'UA' group by uniquecarrier order by avgdelay desc"
  " select avg(depdelay) delay, dest, month_ from airline where month_ in (10, 11, 12, 1) group by month_, dest order by delay"
  " select avg(depdelay) from airline where dayofweek in (1, 2)"
  " select avg(depdelay), origin from airline group by origin order by avg(depdelay) limit 50"
  " select avg(depdelay), uniquecarrier from airline group by uniquecarrier"
  " select avg(depdelay), uniquecarrier from airline where dayofweek in (1, 2) group by uniquecarrier"
  " select avg(arrdelay), count(*) from airline where uniquecarrier = 'UA'"
  " select avg(arrdelay) arrdelay, dest, month_ from airline where month_ in (10, 11, 12, 1) group by month_, dest order by arrdelay"
  " select avg(arrdelay) arrdelay, month_ from airline group by month_ order by arrdelay"
  " select count(*), avg(arrdelay), avg(depdelay), dayofweek from airline group by dayofweek"
  " select count(*), avg(arrdelay), avg(depdelay), dayofweek, month_ from airline group by month_, dayofweek order by month_, dayofweek "
  " select count(*) from (select avg(arrdelay), avg(depdelay) from airline group by flightnum)t1"
  " select count(*) from airline"
  " select count(*), uniquecarrier from airline group by uniquecarrier"
  " select count(*), uniquecarrier, origin from airline group by uniquecarrier, origin order by uniquecarrier, origin"
  " select count(*), uniquecarrier, year_ from airline group by uniquecarrier, year_"
  " select count(*), uniquecarrier, year_, month_ from airline group by year_, month_, uniquecarrier order by year_, month_, uniquecarrier desc"
  " select * from airline limit 1"
  " select max(arrdelay), max(depdelay), uniquecarrier from airline group by uniquecarrier"
  " select max(arrdelay), uniquecarrier from airline group by uniquecarrier"
  " select sum(arrdelay + depdelay), count(*) from airline group by uniquecarrier"
  " select sum(arrdelay + depdelay), count(*) from airline group by uniquecarrier limit 10"
  " select sum(arrdelay + depdelay), count(*) from airline where uniquecarrier = 'AA' group by uniquecarrier"
  " select avg(arrdelay), avg(depdelay) from airline group by flightnum"
  " select avg(arrdelay), avg(depdelay), uniquecarrier from airline group by uniquecarrier"
  " select avg(arrdelay), avg(depdelay), uniquecarrier, year_ from airline group by uniquecarrier, year_"
  " select avg(arrdelay), max(arrdelay), avg(depdelay), max(depdelay), origin from STAGING_AIRLINE group by origin order by avg(arrdelay) desc"
  " select avg(arrdelay), uniquecarrier from STAGING_AIRLINE group by uniquecarrier"
  " select avg(depdelay) avgdelay, uniquecarrier from STAGING_AIRLINE group by uniquecarrier order by avgdelay desc"
  " select avg(depdelay) from STAGING_AIRLINE where dayofweek in (1, 2)"
  " select avg(depdelay), origin from STAGING_AIRLINE group by origin order by avg(depdelay) limit 50"
  " select avg(arrdelay) arrdelay, dest, month_ from STAGING_AIRLINE where month_ in (10, 11, 12, 1) group by month_, dest order by arrdelay"
  " select count(*) from (select avg(arrdelay), avg(depdelay) from STAGING_AIRLINE group by flightnum)t1"
  " select count(*), uniquecarrier, year_, month_ from STAGING_AIRLINE group by year_, month_, uniquecarrier order by year_, month_, uniquecarrier desc"
  " select * from STAGING_AIRLINE limit 1"
  " select max(arrdelay), max(depdelay), uniquecarrier from STAGING_AIRLINE group by uniquecarrier"
  " select sum(arrdelay + depdelay), count(*) from STAGING_AIRLINE group by uniquecarrier limit 10"
  " select sum(arrdelay + depdelay), count(*) from STAGING_AIRLINE where uniquecarrier = 'AA' group by uniquecarrier"
  " select sum(arrdelay + depdelay), count(*) from STAGING_AIRLINE where uniquecarrier = 'DL' group by uniquecarrier"
  " select sum(arrdelay + depdelay), count(*) from STAGING_AIRLINE where uniquecarrier = 'UA' group by uniquecarrier"
  " select sum(arrdelay + depdelay), count(*) from STAGING_AIRLINE where uniquecarrier = 'WN' group by uniquecarrier"
  " select avg(arrdelay), avg(depdelay) from STAGING_AIRLINE group by flightnum"
  " SELECT count(distinct product_id) from Reviews"
  " SELECT product_title from reviews limit 10"
  " SELECT distinct year from reviews order by year"
  " SELECT count(*), year from reviews group by year order by count(*) desc"
  " SELECT count(*), spark_partition_id() from reviews group by spark_partition_id() order by count(*) desc"
  " SELECT distinct customer_id from reviews"
  " SELECT count(distinct customer_id) from reviews"
  " SELECT customer_id, count(*)  from reviews where review_body is not null group by customer_id"
  " SELECT customer_id, count(*)  from reviews where review_body is not null group by customer_id order by count(*) desc limit 20"
  " SELECT product_title from reviews where product_title like '%iPhone%' limit 20"
  " SELECT CUSTOMER_ID,TOTAL_HELPFUL_VOTES FROM (SELECT CUSTOMER_ID,SUM(HELPFUL_VOTES) AS TOTAL_HELPFUL_VOTES FROM STAGING_REVIEWS GROUP BY CUSTOMER_ID) ORDER BY TOTAL_HELPFUL_VOTES DESC LIMIT 10"
  " SELECT count(PRODUCT_ID) count, average_star_rating from (SELECT PRODUCT_ID,ceil(AVG(STAR_RATING)) AS average_star_rating FROM STAGING_REVIEWS GROUP BY PRODUCT_ID) GROUP BY average_star_rating ORDER BY average_star_rating DESC"
  " SELECT * FROM (SELECT CUSTOMER_ID,YEAR,TOTAL_HELPFUL_VOTES,DENSE_RANK() OVER (PARTITION BY YEAR ORDER BY TOTAL_HELPFUL_VOTES DESC ) AS RANK FROM (SELECT CUSTOMER_ID,YEAR,SUM(HELPFUL_VOTES) AS TOTAL_HELPFUL_VOTES FROM STAGING_REVIEWS GROUP BY CUSTOMER_ID, YEAR)) WHERE RANK <= 10"
  " SELECT STAR_RATING, PS.PRODUCT_CATEGORY, COUNT_PER_RATING AS COUNT,COUNT_PER_RATING * 100 / COUNT AS SHARE_IN_PERCENTAGE FROM (SELECT COUNT(*) AS COUNT_PER_RATING,STAR_RATING, product_category FROM STAGING_REVIEWS GROUP BY product_category,STAR_RATING) AS PS JOIN (SELECT COUNT(*) COUNT,product_category FROM STAGING_REVIEWS GROUP BY product_category) P ON p.product_category = ps.product_category ORDER BY PS.PRODUCT_CATEGORY,STAR_RATING DESC"*/
  " SELECT v1.product_id,v1.review_count AS total_STAGING_REVIEWS ,v2.review_count AS review_gt_3_star FROM review_count_GT_10 v1 JOIN REVIEW_count_rating_GT_3 v2 ON v1.PRODUCT_ID = v2.PRODUCT_ID WHERE v2.review_count / v1.review_count > 0.8"
  " SELECT * FROM votes_by_category_and_marketplace v1 JOIN total_customers_per_marketplace_and_category v2 ON v1.marketplace = v2.marketplace AND v1.product_category = v2.product_category WHERE v1.RANK < v2.count * 0.1"
  " SELECT * FROM (SELECT CUSTOMER_ID, MARKETPLACE,	avg_review_length, RANK() OVER (PARTITION BY MARKETPLACE ORDER BY avg_review_length desc) AS rank FROM (SELECT	CUSTOMER_ID, MARKETPLACE, AVG(LENGTH(REVIEW_BODY)) AS avg_review_length FROM	STAGING_REVIEWS	GROUP BY MARKETPLACE, CUSTOMER_ID)) WHERE RANK <= 10"
  " SELECT	product_category, MONTH(REVIEW_DATE), COUNT(*) AS review_COUNT FROM STAGING_REVIEWS GROUP BY MONTH(REVIEW_DATE), product_category ORDER BY product_category, review_COUNT desc"
  " SELECT	* FROM (SELECT word, COUNT, RANK() OVER (ORDER BY COUNT DESC) AS RANK FROM (SELECT word, COUNT(*) AS COUNT FROM	(SELECT explode( split( REVIEW_BODY,'\s+' )) AS word FROM STAGING_REVIEWS ) GROUP BY word)) WHERE RANK <= 20"
  " SELECT * FROM (SELECT product_id, ratio, total_STAGING_REVIEWS, RANK() OVER (ORDER BY total_STAGING_REVIEWS) AS RANK FROM (SELECT v1.product_id, v2.review_count / v1.review_count AS ratio, v1.review_count AS total_STAGING_REVIEWS FROM products_with_votes_lt_50 v1 JOIN rating_gt_3_count v2 ON v1.product_id = v2.product_id) WHERE ratio > 0.8 ) WHERE RANK <= 10"*/
  " SELECT * FROM (SELECT PRODUCT_ID, avg_star_rating, avg_review_length, RANK() OVER (ORDER BY avg_star_rating DESC) AS RANK FROM (SELECT product_id, AVG(STAR_RATING) AS avg_star_rating, AVG(SIZE( split(REVIEW_BODY, '\s+'))) AS avg_review_length FROM STAGING_REVIEWS GROUP BY PRODUCT_ID) WHERE avg_star_rating > 3 AND avg_review_length < 50) WHERE RANK <= 10"
  " SELECT * FROM (SELECT PRODUCT_PARENT, AVG(STAR_RATING) AS avg_rating, COUNT(DISTINCT(PRODUCT_ID)) AS no_of_products FROM STAGING_REVIEWS GROUP BY PRODUCT_PARENT) WHERE no_of_products > 5 AND avg_rating < 2"*/
  " SELECT product_title from STAGING_REVIEWS limit 10"
  " SELECT count(*), spark_partition_id() from STAGING_REVIEWS group by spark_partition_id() order by count(*) desc"
  " SELECT count(distinct customer_id) from STAGING_REVIEWS"
  " SELECT customer_id, count(*)  from STAGING_REVIEWS where review_body is not null group by customer_id"
  " SELECT customer_id, count(*)  from STAGING_REVIEWS where review_body is not null group by customer_id order by count(*) desc limit 20"
  " SELECT product_title from STAGING_REVIEWS where product_title like '%iPhone%' limit 20"
  " SELECT hour(Trip_Pickup_DateTime) as  hour ,avg(trip_distance/((unix_timestamp(Trip_Dropoff_DateTime)-unix_timestamp(Trip_Pickup_DateTime))/3600000000)) as AVG_SPEED from NYCTAXI where hour(Trip_Pickup_DateTime) BETWEEN 8 AND 10 group by Trip_Pickup_DateTime order by hour limit 100"
  " SELECT dayofmonth(Trip_Pickup_DateTime) dayofweek,cast(100*AVG(trip_distance/(unix_timestamp(Trip_Dropoff_DateTime)-unix_timestamp(Trip_Pickup_DateTime)/3600000000))/100 as int)  speed FROM NYCTAXI WHERE Fare_Amt/trip_distance BETWEEN 2 AND 10 AND HOUR(Trip_Pickup_DateTime) BETWEEN 8 AND 18 GROUP BY 1 ORDER BY 1"
  " SELECT dayofmonth(Trip_Pickup_DateTime) AS day, month(Trip_Pickup_DateTime) as month,SUM(Total_Amt) AS total_revenue FROM NYCTAXI GROUP BY dayofmonth(Trip_Pickup_DateTime),month(Trip_Pickup_DateTime)"
  " SELECT vendor_name, COUNT(*) AS number_of_trips FROM NYCTAXI GROUP BY vendor_name"
  " SELECT mm as weekofyear,sum(Total_Amt) as total_revenue from (SELECT weekofyear(Trip_Pickup_DateTime) as mm, Total_Amt from NYCTAXI) ff group by mm order by mm"
  " SELECT avg(trip_distance),vendor_name from NYCTAXI group by vendor_name"
  " SELECT sum(passenger_count) as passengerCnt_perTaxi ,vendor_name from NYCTAXI group by vendor_name"
  " SELECT vendor_name, COUNT(*) AS number_of_trips FROM NYCTAXI GROUP BY vendor_name"
  " SELECT tipped, COUNT(*) AS tip_freq FROM (SELECT CASE WHEN (tip_amt > 0) THEN 1 ELSE 0 END AS tipped, tip_amt FROM NYCTAXI WHERE Trip_Pickup_DateTime BETWEEN cast('2013-01-01' as timestamp) AND cast('2013-12-31' as timestamp)) tc GROUP BY tipped"
  " SELECT SUM(FLOAT(Total_Amt)) FROM  NYCTAXI WHERE cast(YEAR(TIMESTAMP(Trip_Pickup_DateTime)) as int ) = 2013 AND cast(MONTH(TIMESTAMP(Trip_Pickup_DateTime)) as int) = 12"
  " SELECT ROUND(Start_Lat, 4) as lat , ROUND(Start_Lon, 4) as lon, COUNT(*) as num_pickups FROM NYCTAXI  WHERE (Start_Lat BETWEEN 40.61 AND 40.91) AND (Start_Lon BETWEEN -74.06 AND -73.77) GROUP BY 1,2 ORDER BY num_pickups desc"
  " SELECT to_date(Trip_Dropoff_DateTime), COUNT(*) AS c  FROM NYCTAXI GROUP BY to_date(Trip_Dropoff_DateTime)"
  " SELECT hour(NYCTAXI.Trip_Pickup_DateTime),sum(Fare_Amt),sum(surcharge),sum(tip_amt) from NYCTAXI group by 1 order by sum(Fare_Amt) desc limit 30"
  " SELECT sum(passenger_count),sum(tip_amt),payment_type from NYCTAXI  group by payment_type order by 2 desc limit 30"
  " SELECT ROUND(SUM(FLOAT(Total_Amt)),2) AS amt,date_format(Trip_Pickup_DateTime,'dd.mm.yyyy'),vendor_name FROM NYCTAXI GROUP BY 2,vendor_name ORDER BY amt LIMIT 10 "
  " SELECT ROUND(Start_Lat, 4) as lat , ROUND(Start_Lon, 4) as lon, COUNT(*) as num_pickups ,ROUND(sum(Total_Amt),2) as totalAmt FROM NYCTAXI  WHERE (Start_Lat BETWEEN 40.61 AND 40.91) AND (Start_Lon BETWEEN -74.06 AND -73.77) GROUP BY 1,2 ORDER BY num_pickups desc"
  " SELECT hour(Trip_Pickup_DateTime) as  hour ,avg(trip_distance/((unix_timestamp(Trip_Dropoff_DateTime)-unix_timestamp(Trip_Pickup_DateTime))/3600000000)) as AVG_SPEED from STAGING_NYCTAXI where hour(Trip_Pickup_DateTime) BETWEEN 8 AND 10 group by Trip_Pickup_DateTime order by hour limit 100"
  " SELECT mm as weekofyear,sum(Total_Amt) as total_revenue from (SELECT weekofyear(Trip_Pickup_DateTime) as mm, Total_Amt from STAGING_NYCTAXI) ff group by mm order by mm"
  " SELECT avg(trip_distance),vendor_name from STAGING_NYCTAXI group by vendor_name"
  " SELECT sum(passenger_count) as passengerCnt_perTaxi ,vendor_name from STAGING_NYCTAXI group by vendor_name"
  " SELECT tipped, COUNT(*) AS tip_freq FROM (SELECT CASE WHEN (tip_amt > 0) THEN 1 ELSE 0 END AS tipped, tip_amt FROM STAGING_NYCTAXI WHERE Trip_Pickup_DateTime BETWEEN cast('2013-01-01' as timestamp) AND cast('2013-12-31' as timestamp)) tc GROUP BY tipped"
  " SELECT SUM(FLOAT(Total_Amt)) FROM  STAGING_NYCTAXI WHERE cast(YEAR(TIMESTAMP(Trip_Pickup_DateTime)) as int ) = 2013 AND cast(MONTH(TIMESTAMP(Trip_Pickup_DateTime)) as int) = 12"
  " SELECT ROUND(Start_Lat, 4) as lat , ROUND(Start_Lon, 4) as lon, COUNT(*) as num_pickups FROM STAGING_NYCTAXI  WHERE (Start_Lat BETWEEN 40.61 AND 40.91) AND (Start_Lon BETWEEN -74.06 AND -73.77) GROUP BY 1,2 ORDER BY num_pickups desc"
  " SELECT to_date(Trip_Dropoff_DateTime), COUNT(*) AS c  FROM STAGING_NYCTAXI GROUP BY to_date(Trip_Dropoff_DateTime)"
  " SELECT hour(STAGING_NYCTAXI.Trip_Pickup_DateTime),sum(Fare_Amt),sum(surcharge),sum(tip_amt) from STAGING_NYCTAXI group by 1 order by sum(Fare_Amt) desc limit 30"
  " SELECT sum(passenger_count),sum(tip_amt),payment_type from STAGING_NYCTAXI  group by payment_type order by 2 desc limit 30"
  " SELECT ROUND(SUM(FLOAT(Total_Amt)),2) AS amt,date_format(Trip_Pickup_DateTime,'dd.mm.yyyy'),vendor_name FROM STAGING_NYCTAXI GROUP BY 2,vendor_name ORDER BY amt LIMIT 10"
  ;