# Presto Sketch

## Usage

Example query in Presto on for using DataSketches:

```sql
SELECT 
  brand, 
  SUM(user_spent) AS user_spent, 
  thetasketch_estimate(thetasketch_union(sketch)) AS unique_user_count;
FROM schema.table WHERE datestamp = '<date>' AND data_source = '<datasource>'
GROUP BY brand;
```
