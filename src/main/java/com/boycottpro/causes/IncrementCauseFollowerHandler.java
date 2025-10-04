package com.boycottpro.causes;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.models.ResponseMessage;
import com.boycottpro.utilities.JwtUtility;
import com.boycottpro.utilities.Logger;
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
        String sub = null;
        int lineNum = 35;
        try {
            sub = JwtUtility.getSubFromRestEvent(event);
            if (sub == null) {
            Logger.error(38, sub, "user is Unauthorized");
            return response(401, Map.of("message", "Unauthorized"));
            }
            lineNum = 41;
            Map<String, String> pathParams = event.getPathParameters();
            String causeId = (pathParams != null) ? pathParams.get("cause_id") : null;
            String incrementStr = (pathParams != null) ? pathParams.get("increment") : null;
            if (causeId == null || causeId.isEmpty()) {
                Logger.error(47, sub, "cause_id not present");
                ResponseMessage message = new ResponseMessage(400,
                        "cause_id not present", "Missing cause_id");
                return response(400,message);
            }
            if (incrementStr == null || incrementStr.isEmpty()) {
                Logger.error(53, sub, "increment not present");
                ResponseMessage message = new ResponseMessage(400,
                        "increment not present", "Missing increment");
                return response(400,message);
            }
            if (!(incrementStr.equals("true") || incrementStr.equals("false"))) {
                Logger.error(59, sub, "increment not acceptable value");
                ResponseMessage message = new ResponseMessage(400,
                        "increment not acceptable value", "Expected true/false");
                return response(400,message);
            }
            lineNum = 64;
            boolean increment = Boolean.parseBoolean(incrementStr);
            boolean updated = incrementCauseRecord(causeId, increment);
            lineNum = 67;
            return response(200,"cause record updated = " + updated);
        } catch (Exception e) {
            Logger.error(lineNum, sub, e.getMessage());
            return response(500,Map.of("error", "Unexpected server error: " + e.getMessage()) );
        }
    }
    private APIGatewayProxyResponseEvent response(int status, Object body) {
        String responseBody = null;
        try {
            responseBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(responseBody);
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
