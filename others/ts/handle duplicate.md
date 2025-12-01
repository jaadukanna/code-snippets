Handling duplicate messages is critical in an event-driven system, especially when using Kafka, where "at least once" delivery semantics ensure that messages may occasionally be re-delivered. To make your consumers idempotent and ensure they handle duplicate messages gracefully, follow these strategies:


---

1. Use Unique Identifiers for Events

Assign a unique identifier (e.g., eventId or messageId) to each event.

This identifier is used to detect and ignore duplicates.


Example:

{
  "eventId": "12345",
  "eventType": "QuoteRequested",
  "data": { ... },
  "timestamp": "2025-01-16T12:00:00Z"
}


---

2. Maintain a Processed Events Log

Use a persistent store (e.g., MongoDB) or an in-memory cache (e.g., Redis) to keep track of processed eventIds.

Before processing an event, check if the eventId exists in this log.

If it exists, skip processing.

If it doesn't, process the event and add the eventId to the log.



Example MongoDB Schema:

const processedEventSchema = new Schema({
    eventId: { type: String, required: true, unique: true },
    timestamp: { type: Date, required: true },
});

Implementation:

async function processEvent(event: any) {
    const existingEvent = await ProcessedEvent.findOne({ eventId: event.eventId });

    if (existingEvent) {
        console.log(`Duplicate event detected: ${event.eventId}, skipping processing.`);
        return;
    }

    // Process the event
    console.log(`Processing event: ${event.eventId}`);

    // Save the processed event
    await ProcessedEvent.create({ eventId: event.eventId, timestamp: new Date() });
}


---

3. Leverage Kafka Consumer Offsets

Kafka stores consumer offsets to track which messages have been consumed.

Use manual offset commits to ensure that offsets are only committed after successful processing of a message.

This prevents the same message from being processed multiple times during retries.


Implementation with KafkaJS:

await consumer.run({
    eachMessage: async ({ topic, partition, message }) => {
        try {
            const event = JSON.parse(message.value?.toString() || '{}');
            
            // Process the event (ensure idempotency)
            await processEvent(event);

            // Commit offset after successful processing
            await consumer.commitOffsets([{ topic, partition, offset: (parseInt(message.offset) + 1).toString() }]);
        } catch (error) {
            console.error(`Error processing message: ${message.offset}, retrying...`);
            // Do not commit offset, ensuring retries
        }
    },
});


---

4. Use Atomic Database Transactions

If your consumer updates multiple resources (e.g., appending to an event log and updating an entity's state), wrap the operations in a database transaction.

Ensure the transaction is idempotent by using the eventId as part of the transaction logic.


MongoDB Example:

const session = await mongoose.startSession();
session.startTransaction();

try {
    const existingEvent = await EventLog.findOne({ eventId: event.eventId }).session(session);

    if (!existingEvent) {
        // Append to event log
        await EventLog.create([event], { session });

        // Update entity state
        await Entity.updateOne(
            { entityId: event.data.entityId },
            { state: event.data.state, updatedAt: new Date() },
            { upsert: true, session }
        );
    }

    await session.commitTransaction();
} catch (error) {
    await session.abortTransaction();
    console.error(`Transaction failed: ${error.message}`);
} finally {
    session.endSession();
}


---

5. Idempotent Event Handlers

Design your business logic to be idempotent by ensuring:

State updates are conditional.

Side effects (e.g., sending emails) are only executed once.



Example:

await Entity.updateOne(
    { entityId: event.data.entityId, state: { $ne: event.data.state } }, // Update only if state differs
    { state: event.data.state, updatedAt: new Date() }
);


---

6. Dead-Letter Queues (DLQs)

If a message repeatedly fails processing (e.g., due to corruption or missing data), move it to a dead-letter queue for manual inspection.

Kafka supports DLQs natively via retry topics.



---

7. Use a Schema Registry

Maintain strict schema validation using a schema registry (e.g., Confluent Schema Registry) to prevent invalid messages from entering the pipeline.

This minimizes duplicate processing caused by malformed events.



---

8. Implement Retry Logic

If an error occurs, retry processing a fixed number of times before marking the event as failed.

Use exponential backoff to avoid overwhelming downstream systems.


Retry Example:

const MAX_RETRIES = 3;

async function processWithRetry(event: any, retries = 0) {
    try {
        await processEvent(event);
    } catch (error) {
        if (retries < MAX_RETRIES) {
            console.log(`Retrying event: ${event.eventId}, attempt ${retries + 1}`);
            await processWithRetry(event, retries + 1);
        } else {
            console.error(`Failed to process event: ${event.eventId}, moving to DLQ`);
            await dlqProducer.send({ topic: 'dead-letter-queue', messages: [{ value: JSON.stringify(event) }] });
        }
    }
}


---

Summary of Steps

1. Unique Identifiers: Use eventId to identify duplicates.


2. Processed Events Log: Track already processed events in a database or cache.


3. Consumer Offsets: Commit offsets only after successful processing.


4. Atomic Transactions: Ensure idempotency in database updates.


5. Event Handlers: Design idempotent business logic.


6. Dead-Letter Queues: Handle irrecoverable errors gracefully.


7. Retry Mechanisms: Implement retries with backoff strategies.



By combining these strategies, you can build a robust system that gracefully handles duplicate messages while maintaining data integrity and system reliability.

