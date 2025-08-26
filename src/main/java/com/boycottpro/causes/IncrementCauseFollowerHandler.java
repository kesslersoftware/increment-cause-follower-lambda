package com.boycottpro.causes;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.models.ResponseMessage;
import com.boycottpro.utilities.JwtUtility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

public class IncrementCauseFollowerHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TABLE_NAME = "causes";
    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IncrementCauseFollowerHandler() {
        this.dynamoDb = DynamoDbClient.create();
    }

    public IncrementCauseFollowerHandler(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String sub = JwtUtility.getSubFromRestEvent(event);
            if (sub == null) return response(401, "Unauthorized");
            Map<String, String> pathParams = event.getPathParameters();
            String causeId = (pathParams != null) ? pathParams.get("cause_id") : null;
            String incrementStr = (pathParams != null) ? pathParams.get("increment") : null;

            if (causeId == null || causeId.isEmpty()) {
                return response(400, "cause_id not present", "Missing cause_id");
            }

            if (incrementStr == null || incrementStr.isEmpty()) {
                return response(400, "increment not present", "Missing increment");
            }

            if (!(incrementStr.equals("true") || incrementStr.equals("false"))) {
                return response(400, "increment not acceptable value", "Expected true/false");
            }

            boolean increment = Boolean.parseBoolean(incrementStr);
            boolean updated = incrementCauseRecord(causeId, increment);

            String responseBody = objectMapper.writeValueAsString("cause record updated = " + updated);
            return response(200,responseBody);
        } catch (Exception e) {
            e.printStackTrace();
            return response(500, "Transaction failed: " + e.getMessage(), "Internal Server Error");
        }
    }
    private APIGatewayProxyResponseEvent response(int status, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(body);
    }
    private boolean incrementCauseRecord(String causeId, boolean increment) {
        try {
            int delta = increment ? 1 : -1;

            Map<String, AttributeValue> key = Map.of("cause_id", AttributeValue.fromS(causeId));
            Map<String, AttributeValue> values = new HashMap<>();
            values.put(":delta", AttributeValue.fromN(Integer.toString(delta)));
            values.put(":zero", AttributeValue.fromN("0"));

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(key)
                    .updateExpression("SET follower_count = if_not_exists(follower_count, :zero) + :delta")
                    .expressionAttributeValues(values)
                    .conditionExpression("attribute_exists(cause_id)")
                    .build();

            dynamoDb.updateItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            System.err.println("Cause not found: " + causeId);
            throw e;
        } catch (DynamoDbException e) {
            e.printStackTrace();
            throw e;
        }
    }

    private APIGatewayProxyResponseEvent response(int status, String body, String dev) {
        try {
            String json = objectMapper.writeValueAsString(new ResponseMessage(status, body, dev));
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(status)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
