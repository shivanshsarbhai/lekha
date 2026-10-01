package com.lekha.categories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	CategoryService service;

	@Test
	void listReturnsTheTreeAsJson() {
		UUID food = UUID.fromString("11111111-1111-1111-1111-111111111111");
		UUID dining = UUID.fromString("22222222-2222-2222-2222-222222222222");
		UUID salary = UUID.fromString("33333333-3333-3333-3333-333333333333");
		given(service.tree()).willReturn(List.of(
				new CategoryNode(food, "Food", CategoryKind.EXPENSE,
						List.of(new CategoryNode(dining, "Dining out", CategoryKind.EXPENSE, List.of()))),
				new CategoryNode(salary, "Salary", CategoryKind.INCOME, List.of())));

		assertThat(mvc.get().uri("/api/v1/categories"))
				.hasStatusOk()
				.bodyJson()
				.isStrictlyEqualTo("""
						[
						  {
						    "id": "11111111-1111-1111-1111-111111111111",
						    "name": "Food",
						    "kind": "EXPENSE",
						    "children": [
						      {
						        "id": "22222222-2222-2222-2222-222222222222",
						        "name": "Dining out",
						        "kind": "EXPENSE",
						        "children": []
						      }
						    ]
						  },
						  {
						    "id": "33333333-3333-3333-3333-333333333333",
						    "name": "Salary",
						    "kind": "INCOME",
						    "children": []
						  }
						]
						""");
	}

}
