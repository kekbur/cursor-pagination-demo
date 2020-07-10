# cursor-pagination-demo

This project demonstrates how to use [keyset pagination without numeric offsets](https://use-the-index-luke.com/no-offset) together with blaze-persistence and Spring Boot. This approach is also called cursor based pagination or seek pagination and is suitable for example for infinite scrolling.

Adapted from [blaze-persistence-examples-spring-hateoas](https://github.com/Blazebit/blaze-persistence/tree/master/examples/spring-hateoas).

## Running the Application

Launch the main class `com.github.kekbur.cursorpaginationdemo.Application`

## Sample Request URLs

| Description | URL |
| --- | --- |
| Retrieve first 10 cats | [http://localhost:8080/cats] |
| Retrieve first 10 cats whose name contains "3" | [http://localhost:8080/cats?filter=%7B%22field%22:%22name%22,%22values%22:%5B%223%22%5D,%22kind%22:%22CONTAINS%22%7D] |
| Retrieve all cats (no pagination) | [http://localhost:8080/all-cats] |

The above requests sort cats by age (ascending) and secondarily by id (descending).

Use the "next" and "previous" links in the responses to navigate back and forward.

## Sample Response

Note that the cat attributes are randomly generated when the application starts, so the "next" link below won't work for you.

```json
{
    "next": "http://localhost:8080/cats?after=AgEAAAABAAAAAAAAAABg",
    "content": [
        {
            "id": 101,
            "name": "Cat 96",
            "age": 0,
            "owner": {
                "id": 4,
                "name": "Person 3"
            },
            "mother": null,
            "father": null
        },
        {
            "id": 87,
            "name": "Cat 82",
            "age": 0,
            "owner": {
                "id": 2,
                "name": "Person 1"
            },
            "mother": null,
            "father": null
        },
        <...>
    }
}
```

## Generated SQL Queries

Retrieving the first 10 cats whose name contains "3":

```sql
<...> where cat0_.name like '%3%' order by cat0_.age ASC, cat0_.id DESC limit 10
```

Retrieving the next 10 cats:

```sql
<...> where (7, cat0_.id) < (cat0_.age, 35) and 0=0 and (cat0_.name like '%3%') order by cat0_.age ASC, cat0_.id DESC limit 10
```
Take note how the desired rows are found using a `where` condition instead of using a numeric offset. Such queries can be executed more efficiently as long as the tables have appropriate indexes. See [https://use-the-index-luke.com/sql/partial-results/fetch-next-page] for more information.
