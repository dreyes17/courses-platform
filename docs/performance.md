# Performance: virtual threads and caching

- [Virtual threads](#virtual-threads)
- [Catalog cache](#catalog-cache)

## Virtual threads

With `spring.threads.virtual.enabled=true`, three thread sources switch to virtual threads:

| Where | Before | Now |
|---|---|---|
| HTTP requests (Tomcat) | pool of 200 platform threads | one virtual thread per request |
| `@RabbitListener` consumers | listener container platform threads | virtual threads (`rabbit-simple-N` in the logs) |
| `@Scheduled` tasks (outbox relay) | one platform thread | virtual threads |

**Why it helps here.** Almost all of a request's or message's time is spent waiting on PostgreSQL or RabbitMQ.
With platform threads, each wait holds one of Tomcat's 200 threads, and a burst of slow requests queues everything
else even with idle CPU. A blocked virtual thread takes almost no memory and releases its carrier thread, so the
number of in-flight requests is no longer bounded by a pool size.

**What doesn't change.** The real limit on database work is still the Hikari pool (10 connections by default):
with virtual threads, requests over that limit wait for a connection instead of a thread. Nor does the capacity
guarantee change, which comes from the conditional `UPDATE`, not from the thread count.

**The Java 21 risk: *pinning*.** In Java 21, a virtual thread that blocks inside a `synchronized` block doesn't
release its carrier thread. Java 24 fixes it (JEP 491). Rather than assume, I measured it: the full suite ran with
`-Djdk.tracePinnedThreads=short`, which logs every occurrence, and none appeared. That run includes the
concurrency test, the RabbitMQ flows, the outbox relay and JDBC access. To repeat it:

```bash
./mvnw test -DargLine="-Djdk.tracePinnedThreads=short"   # any case would show up with its stack trace
```

`VirtualThreadsTest` checks each source by running work on it: a consumer created with the same factory as the
`@RabbitListener`s and a task on the relay's scheduler. For Tomcat it checks its executor, since MockMvc doesn't
go through the server. With the property turned off, all three tests fail.

## Catalog cache

Spring Cache with Caffeine (in memory), configured in `shared.config.CacheConfig`:

| Cache | Holds | TTL | Evicted on... |
|---|---|---|---|
| `categories` | category by id | 10 min | editing, archiving, activating or deleting that category |
| `category-pages` | pages of the unfiltered category list (key: `page`, `size`, `sort`); filtered ones aren't cached because they rarely repeat | 10 min | any category change |
| `instructors` | instructor by id | 10 min | editing or deleting that instructor |
| `courses` | course by id, with its available seats | 1 min | editing, publishing, archiving or deleting the course; **reserving or releasing a seat**; renaming its category or instructor |

TTLs are configured with `CACHE_CATALOG_TTL` and `CACHE_COURSE_TTL`.

**What isn't cached: course search.** It combines six filters with pagination and sorting, so the same key rarely
repeats. It also includes the available-seats filter, which changes with every enrollment: each reservation would
force evicting every search. A lot of eviction cost for few hits. A course's detail view does pay off: it's the
most repeated query, and it's evicted precisely, only for that course.

**How stale data is avoided:**

- **Seat counts never lag.** `availableSeats` doesn't change in `CourseService` but in the atomic `UPDATE`. So
  eviction lives in the repository itself: `@CacheEvict` on `tryReserveSeat` and `releaseSeat`. Any path that
  touches seats (enroll, cancel, failed payment) evicts the course, without each service having to remember.
- **Eviction after *commit*.** The `CacheManager` is *transaction-aware*: an eviction inside a transaction applies
  when it commits. If it applied earlier, another request could re-cache the old row in that window; and on
  *rollback*, nothing is evicted.
- **Permissions don't depend on the cache.** A course is cached whatever its status, but the visibility rule (a
  draft is only visible to ADMIN and its instructor) is checked on every call, cache hits included. That's why the
  cache lives in a separate bean, `CourseViewCache`: the check can never be skipped, and Spring's proxy
  intercepts the call (it doesn't intercept a bean's calls to itself).
- **Embedded names.** A course's view includes its category's and instructor's names, so renaming either one
  clears the course cache. It's an infrequent admin operation.

**Known limit: the cache is local to each instance.** With several instances, a change only evicts the cache of
the instance that made it, and the others may serve the previous value for at most the TTL: 1 minute for courses
and 10 for the rest. Reservations aren't affected, because the atomic `UPDATE` always reads the database; what may
lag is the displayed seat count. At this scale that's acceptable. If it weren't, swapping the `CacheManager` for a
Redis one would be enough: annotations and evictions stay the same.

**Metrics.** `cache_gets_total{cache, result="hit"|"miss"}`, together with `cache_puts_total` and
`cache_evictions_total`, on `/actuator/prometheus`: each cache's hit rate is visible directly.

`CatalogCacheTest` checks that:

- the second read doesn't reach the database;
- a change made through the application is visible immediately;
- seats are up to date right after enrolling and cancelling;
- renaming an instructor refreshes their courses;
- a draft cached by an ADMIN stays hidden from students;
- hit metrics are exported.
