package org.bee.retail.controller;

import org.bee.configs.BankingServicesApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.Matchers.nullValue;

@SpringBootTest(classes = BankingServicesApplication.class)
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAllProducts_returnsSeededProducts() throws Exception {
        mockMvc.perform(get("/v1/product/allproducts"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getProductById_existingId_returnsProduct() throws Exception {
        mockMvc.perform(get("/v1/product/productId/101"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("101"))
                .andExpect(jsonPath("$.productName").value("American 24K_Gold"));
    }

    @Test
    void getProductById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/v1/product/productId/does-not-exist"))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void addProduct_returnsCreatedProduct() throws Exception {
        String newProductJson = """
                {
                    "productId": "118",
                    "productName": "American Diamond",
                    "quantity": "10ct",
                    "price": 500000.0
                }
                """;

        mockMvc.perform(post("/v1/product/addproduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newProductJson))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("118"))
                .andExpect(jsonPath("$.productName").value("American Diamond"));
    }

    @Test
    void productMessage_returnsConfirmationText() throws Exception {
        mockMvc.perform(get("/v1/product/productmessage"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("Product Messages"));
    }

    // Regression test for a seed-data bug where products 111-117 were stored under a
    // key one off from their own productId field (e.g. id "112" stored under key "111").
    @ParameterizedTest
    @ValueSource(strings = {"101", "102", "103", "104", "105", "106", "107", "108", "109", "110",
            "112", "113", "114", "115", "116", "117"})
    void getProductById_seededId_returnsProductWithMatchingId(String productId) throws Exception {
        mockMvc.perform(get("/v1/product/productId/" + productId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId));
    }

    @Test
    void getProductById_neverSeededId_returns404() throws Exception {
        // "111" is a gap in the seed data - no product was ever assigned this id.
        mockMvc.perform(get("/v1/product/productId/111"))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void addProduct_existingId_overwritesPreviousValues() throws Exception {
        String original = """
                {
                    "productId": "119",
                    "productName": "Original Name",
                    "quantity": "1kg",
                    "price": 100.0
                }
                """;
        String updated = """
                {
                    "productId": "119",
                    "productName": "Updated Name",
                    "quantity": "2kg",
                    "price": 200.0
                }
                """;

        mockMvc.perform(post("/v1/product/addproduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(original))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/product/addproduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updated))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Updated Name"));

        mockMvc.perform(get("/v1/product/productId/119"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Updated Name"))
                .andExpect(jsonPath("$.quantity").value("2kg"));
    }

    @Test
    void addProduct_missingProductId_isStoredWithNullId() throws Exception {
        String noIdProduct = """
                {
                    "productName": "No ID Product",
                    "quantity": "1",
                    "price": 10.0
                }
                """;

        mockMvc.perform(post("/v1/product/addproduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noIdProduct))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(nullValue()));
    }

    @Test
    void getProductById_postMethod_returns405() throws Exception {
        mockMvc.perform(post("/v1/product/productId/101"))
                .andDo(print())
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void addProduct_getMethod_returns405() throws Exception {
        mockMvc.perform(get("/v1/product/addproduct"))
                .andDo(print())
                .andExpect(status().isMethodNotAllowed());
    }
}
