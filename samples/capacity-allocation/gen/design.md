# Capacity Allocation System Design

## System Overview

The Capacity Allocation System manages the allocation of fixed capacity units
across a high-scale environment, handling millions of capacity units and
potentially millions of users. The system enforces allocation rules, maintains
consistency of allocations, and provides high availability through sharding and
eventual consistency patterns.

## Key Components

### Core Domain Entities

1. **CapacityPool**
   - Represents the total fixed capacity being managed
   - Contains metadata and aggregate capacity information
   - Defines allocation rules and constraints for all users

2. **CapacityShard**
   - Contains a subset of the total capacity
   - Processes allocation operations with strict consistency
   - Maintains allocation status (available, reserved, allocated)
   - Primary unit of distribution in the system

3. **UserActivity**
   - Tracks allocation activity for a user per capacity pool
   - Enforces user-level constraints based on pool rules
   - Sharded by poolId + userId for scalability

4. **Reservation**
   - Represents a temporary hold on capacity
   - Has lifecycle states (pending, confirmed, released)
   - Contains metadata and audit information

5. **AllocationRule**
   - Defines constraints for capacity allocation
   - Examples: maximum per user, time-based limits, priority rules
   - Referenced by CapacityPool and used by UserActivity

### Component Architecture

1. **CapacityPoolEntity**
   - Implemented as an `EventSourcedEntity`
   - Represents the top-level aggregation of capacity for a specific resource type
   - Manages creation, configuration, and metadata of capacity pools
   - Maintains allocation rule definitions for the entire pool
   - Handles updates to pool configuration and allocation rules
   - Configures the number of capacity shards for the pool
   - Triggers distribution of updated allocation rules to shards

2. **CapacityShardEntity**
   - Implemented as an `EventSourcedEntity`
   - Responsible for maintaining allocation integrity within a shard
   - Processes reserve/confirm/release operations
   - Maintains only pending reservations in state
   - Confirmed and released reservations are not stored in state (only as events)
   - Event sources each operation for reliability and auditing
   - Includes pool allocation rules in reservation responses

3. **UserActivityEntity**
   - Implemented as an `EventSourcedEntity`
   - Validates allocation requests against user constraints
   - Uses allocation rules from the pool to enforce limits
   - Records allocation history per user
   - Provides user-specific activity metrics

4. **AllocationCoordinator**
   - Implemented as a `Consumer`
   - Listens for validation events from UserActivityEntity
   - Sends confirmation or release commands to CapacityShardEntity
   - Ensures eventual completion of the allocation process

5. **ReservationRecoveryProcessor**
   - Implemented as a `TimedAction`
   - Periodically checks for unprocessed reservations
   - Resubmits reservations for validation
   - Ensures eventual consistency without timeouts

6. **CapacityView**
   - Implemented as a `View`
   - Consumes events from CapacityShardEntity
   - View of capacity status across shards
   - Provides read-only access to capacity metrics
   - Supports monitoring dashboards and capacity planning

7. **UserActivitiesView**
   - Implemented as a `View`
   - Consumes events from UserActivityEntity
   - Provides cross-pool activity queries for a particular user
   - Supports activity history and status reporting

8. **AllocationEndpoints**
   - Implemented as `HttpEndpoint`
   - HTTP endpoints for allocation operations
   - Leverages the CapacityShardClient for shard routing
   - Provides client-facing APIs

### Sharding Client Details

1. **ShardSelection**
   - Implements consistent hashing algorithm
   - Maps user IDs to appropriate shards
   - Maintains shard distribution map
   - Provides optimal shard selection for new reservations

2. **FallbackStrategy**
   - Implements "power of two choices" for load balancing
   - Selects alternative shards when primary shard is exhausted
   - Prioritizes shards with most available capacity

3. **CapacityTracking**
   - Requests to shards will return information on the capacity status
   - Used for tracking when capacity is exhausted in a shard
   - Used by the fallback strategy for prioritizing shard choice

3. **CapacityShardClient**
   - Provides typed interface to CapacityShard entities
   - Implements shard selection and fallback mechanisms

## Functional Flow

### Reservation Process

1. **Initial Request**
   - Client submits reservation request via endpoint
   - CapacityShardClient selects appropriate capacity shard
   - Request is forwarded to primary shard for processing

2. **Capacity Reservation**
   - Shard validates availability and creates pending reservation
   - Attaches pool allocation rules to reservation context
   - Emits `CapacityReserved` event if successful
   - Returns reservation ID and allocation rules to client

3. **User Activity Validation**
   - Reservation triggers asynchronous validation against user activity
   - UserActivity applies allocation rules from reservation context
   - Emits `AllocationValidated` event with approval status

4. **Confirmation**
   - AllocationCoordinator listens for validation events
   - For approved validations, sends confirmation command to original shard
   - For rejections, sends release command to original shard
   - Shard emits `AllocationConfirmed` or `ReservationReleased` event

5. **Failure Recovery**
   - ReservationRecoveryProcessor periodically checks for unprocessed reservations
   - Resubmits older reservations for validation
   - UserActivityEntity processes validation idempotently
   - Ensures eventual consistency without explicit timeouts

### Pool Rules Flow

1. **Pool Creation**
   - Administrator defines a capacity pool with allocation rules
   - Rules are stored with the pool definition
   - Rules are distributed to capacity shards on creation

2. **Rule Distribution**
   - When capacity shards are created, they receive the current rules for their pool

3. **Rule Application**
   - During reservation, CapacityShard includes rules in reservation context
   - UserActivity receives rules as part of validation request
   - Validation applies rules to current user state for decision

### User Activities View Flow

1. **Event Processing**
   - UserActivitiesView consumes events from all UserActivityEntity instances
   - Creates materialized rows keyed by userId with poolId as attribute
   - Updates activity status on each event
   - Maintains history of activity across all pools

2. **Query Handling**
   - View processes queries for all activity by userId
   - Returns consolidated activity from all pools
   - Supports filtering by activity status, pool, and time periods

## Scalability Design

### Shard Management

1. **Sharding Strategy**
   - Capacity divided into fixed-size shards
   - Each shard handles a subset of total capacity
   - Consistent hashing used for shard selection

2. **Load Distribution**
   - Primary allocation strategy uses userId for routing
   - Fallback strategies handle shard exhaustion
   - "Power of two choices" for load balancing across shards

### User Activity Distribution

1. **Activity Sharding**
   - User activities sharded by poolId + userId
   - Each activity independently managed and validated
   - Supports millions of concurrent users

## Consistency Model

### Allocation Consistency

1. **Shard-level Consistency**
   - Strong consistency within each shard
   - No over-allocation within a shard's capacity
   - Sequential processing of commands per shard

2. **System-wide Eventual Consistency**
   - Aggregate capacity views eventually consistent
   - Recovery process ensures all reservations reach terminal state
   - Idempotent validation ensures correctness during reprocessing

### Reservation Lifecycle

1. **Two-phase Process**
   - Initial reservation creates temporary hold
   - Validation determines final outcome
   - AllocationCoordinator drives process to completion

2. **Retry-based Recovery**
   - No explicit timeouts for reservations
   - Periodic reprocessing of outstanding reservations
   - Idempotency ensures correct handling of duplicates

## Recovery and Resilience

### Failure Recovery

1. **Unprocessed Reservation Recovery**
   - Background process detects old unprocessed reservations
   - Resubmits for validation without changing reservation state
   - Idempotent processing ensures correctness

2. **Entity Failure Recovery**
   - Event sourcing enables entity recovery after failure
   - State rebuilt from persisted events
   - No data loss during recovery

## Implementation Strategy

### Core Data Types

1. **AllocationRule**
   ```
   AllocationRule {
     ruleName: String
     ruleType: Enum(MAX_PER_USER, RATE_LIMIT, PRIORITY, etc.)
     parameters: Map<String, String>
     description: String
   }
   ```

2. **CapacityPool State**
   ```
   CapacityPool {
     poolId: String
     name: String
     description: String
     totalCapacity: Int
     numShards: Int
     allocationRules: List<AllocationRule>
     createdAt: Timestamp
   }
   ```

3. **CapacityShard State**
   ```
   CapacityShard {
     shardId: String
     poolId: String
     totalCapacity: Int
     availableCapacity: Int
     reservedCapacity: Int
     allocatedCapacity: Int
     pendingReservations: Map<ReservationId, PendingReservation>  // Only stores pending reservations
     pendingTimestampIndex: SortedMap<Timestamp, ReservationId>   // For tracking age of pending reservations
   }
   ```

4. **UserActivity State**
   ```
   UserActivity {
     poolId: String
     userId: String
     currentAllocations: Int
     allocationHistory: List<AllocationRecord>
     lastActivityTime: Timestamp
   }
   ```

5. **Reservation States**
   ```
   PendingReservation {
     reservationId: String
     userId: String
     reservedAt: Timestamp
     allocationRulesSnapshot: List<AllocationRule>
     metadata: Map<String, String>
   }
   ```

6. **UserActivityView Row**
   ```
   UserActivityViewRow {
     userId: String
     poolId: String
     status: Enum(ACTIVE, INACTIVE)
     currentAllocations: Int
     lastActivityTime: Timestamp
     poolName: String
     activitySummary: String
   }
   ```

### Core Event Types

1. **CapacityPool Events**
   ```
   PoolCreated {
     poolId: String
     name: String
     description: String
     totalCapacity: Int
     numShards: Int
     allocationRules: List<AllocationRule>
     timestamp: Timestamp
   }

   PoolConfigurationUpdated {
     poolId: String
     name: String
     description: String
     numShards: Int
     timestamp: Timestamp
   }

   AllocationRulesUpdated {
     poolId: String
     allocationRules: List<AllocationRule>
     timestamp: Timestamp
   }

   PoolCapacityChanged {
     poolId: String
     previousCapacity: Int
     newCapacity: Int
     timestamp: Timestamp
   }

   PoolDeactivated {
     poolId: String
     reason: String
     timestamp: Timestamp
   }

   PoolReactivated {
     poolId: String
     timestamp: Timestamp
   }
   ```

2. **CapacityShard Events**
   ```
   CapacityReserved {
     reservationId: String
     userId: String
     timestamp: Timestamp
     allocationRules: List<AllocationRule>
   }

   AllocationConfirmed {
     reservationId: String
     timestamp: Timestamp
   }

   ReservationReleased {
     reservationId: String
     timestamp: Timestamp
     reason: String
   }
   ```

3. **UserActivity Events**
   ```
   AllocationApproved {
     reservationId: String
     poolId: String
     shardId: String
     timestamp: Timestamp
   }

   AllocationRejected {
     reservationId: String
     poolId: String
     shardId: String
     reason: String
     timestamp: Timestamp
   }
   ```

### Command Handlers

1. **CapacityPool Commands**
   - `CreatePool(poolId, name, description, totalCapacity, numShards, allocationRules)`
   - `UpdatePoolConfiguration(poolId, name, description, numShards)`
   - `UpdateAllocationRules(poolId, allocationRules)`
   - `ChangePoolCapacity(poolId, newTotalCapacity)`
   - `DeactivatePool(poolId)`
   - `ReactivatePool(poolId)`
   - `GetPoolStatus(poolId)`

2. **CapacityShard Commands**
   - `ReserveCapacity(reservationId, userId)`
   - `ConfirmAllocation(reservationId)`
   - `ReleaseReservation(reservationId, reason)`
   - `CheckUnprocessedReservations(ageThreshold)`
   - `GetCapacityStatus()`

3. **UserActivity Commands**
   - `Allocate(reservationId, poolId, shardId, allocationRules)`
   - `GetAllocationHistory()`

### Component Interactions

1. **Shard Interactions**
   - Components interact directly with CapacityShard entities using component client
   - No need for REST APIs for internal shard operations

2. **Recovery Process**
   - ReservationRecoveryProcessor uses component client to interact with CapacityShards and UserActivity entities
   - Retrieves unprocessed reservations and resubmits them for validation

3. **AllocationCoordinator**
   - Uses component client to send commands to CapacityShards
   - Listens for validation events via consumer mechanism

### External APIs

1. **CapacityAPI**
   - `POST /capacity/pools` - Create capacity pool with rules
   - `POST /capacity/reservations` - Create reservation
   - `GET /capacity/status` - Get capacity status

2. **UserAPI**
   - `GET /users/{id}/activities` - Get all activities for user
   - `GET /users/{id}/activities/{poolId}` - Get user activity for specific pool

### UserActivitiesView Queries

1. **GetAllUserActivities**
   ```
   @Query("SELECT * FROM user_activities WHERE userId = :userId")
   public QueryEffect<UserActivitiesList> getAllActivities(String userId) {
     return queryResult();
   }
   ```

2. **GetUserActivitiesByStatus**
   ```
   @Query("SELECT * FROM user_activities WHERE userId = :userId AND status = :status")
   public QueryEffect<UserActivitiesList> getActivitiesByStatus(String userId, String status) {
     return queryResult();
   }
   ```

3. **GetRecentUserActivities**
   ```
   @Query("SELECT * FROM user_activities WHERE userId = :userId ORDER BY lastActivityTime DESC LIMIT :limit")
   public QueryEffect<UserActivitiesList> getRecentActivities(String userId, int limit) {
     return queryResult();
   }
   ```

This design provides a comprehensive foundation for implementing a high-scale
Capacity Allocation System with a focus on user activity tracking across pools
and rule-based allocation enforcement. The UserActivitiesView enables cross-pool
activity queries, complementing the pool-specific queries provided by individual
UserActivity entities.
