package com.baeldung.test;

import com.baeldung.Application;
import com.baeldung.spring.TestDbConfig;
import com.baeldung.spring.TestIntegrationConfig;
import io.restassured.RestAssured;
import io.restassured.authentication.FormAuthConfig;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.hamcrest.Matchers.containsString;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { Application.class, TestDbConfig.class, TestIntegrationConfig.class }, webEnvironment = WebEnvironment.RANDOM_PORT)
public class ManagementIntegrationTest {
    @Value("${local.server.port}")
    int port;

    private final FormAuthConfig formConfig = new FormAuthConfig("/login", "username", "password");

    @BeforeEach
    public void init() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    public void givenNotAuthenticatedUser_whenLoggingInAsManager_thenRendersManagementPage() {
        final RequestSpecification request = RestAssured.given().auth().form("manager@test.com", "manager", formConfig);

        request.when().get("/management").then().assertThat().statusCode(200).and().body(containsString("Manager"));
    }

    @Test
    public void givenNotAuthenticatedUser_whenLoggingInAsNotManager_thenRedirectsToForbiddenPage() {
        final RequestSpecification request = RestAssured.given().auth().form("test@test.com", "test", formConfig);

        request.when().get("/management").then().assertThat().statusCode(200).and().body(containsString("Unauthorized"));
    }
}
