package com.arc.orderslambda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry-point for the Orders-Search Lambda function.
 *
 * <p>Spring Cloud Function discovers {@code OrdersSearchFunction} automatically
 * via the {@code spring.cloud.function.definition} property in {@code application.yml}.
 * The AWS adapter ({@code SpringBootRequestHandler}) is the actual Lambda handler class
 * configured in the Terraform resource — this class just bootstraps the application context.
 */
@SpringBootApplication
public class OrdersLambdaApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrdersLambdaApplication.class, args);
    }
}
