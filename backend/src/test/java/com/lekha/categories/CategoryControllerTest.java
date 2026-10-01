package com.lekha.categories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

	private static final UUID FOOD = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static final UUID SNACKS = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	CategoryService service;

	@Test
	void listReturnsTheTreeAsJson() {
		UUID salary = UUID.fromString("33333333-3333-3333-3333-333333333333");
		given(service.tree()).willReturn(List.of(
				new CategoryNode(FOOD, "Food", CategoryKind.EXPENSE,
						List.of(new CategoryNode(SNACKS, "Snacks", CategoryKind.EXPENSE, List.of()))),
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
						        "name": "Snacks",
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

	@Test
	void createReturns201WithLocationAndTheCategory() {
		given(service.create("Snacks", null, FOOD)).willReturn(snacks());

		assertThat(mvc.post().uri("/api/v1/categories")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Snacks", "parentId": "11111111-1111-1111-1111-111111111111"}
						"""))
				.hasStatus(HttpStatus.CREATED)
				.hasHeader("Location", "/api/v1/categories/22222222-2222-2222-2222-222222222222")
				.bodyJson()
				.isStrictlyEqualTo("""
						{
						  "id": "22222222-2222-2222-2222-222222222222",
						  "parentId": "11111111-1111-1111-1111-111111111111",
						  "name": "Snacks",
						  "kind": "EXPENSE",
						  "createdAt": "2026-10-01T10:00:00Z"
						}
						""");
	}

	@Test
	void createReturns400WithTheReasonForAnInvalidCategory() {
		given(service.create("Pets", null, null))
			.willThrow(new InvalidCategoryException("A top-level category needs a kind"));

		assertThat(mvc.post().uri("/api/v1/categories")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Pets"}
						"""))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("A top-level category needs a kind");
	}

	@Test
	void createReturns409ForADuplicateName() {
		given(service.create("Groceries", null, FOOD))
			.willThrow(new DuplicateCategoryNameException("Groceries", new RuntimeException()));

		assertThat(mvc.post().uri("/api/v1/categories")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Groceries", "parentId": "11111111-1111-1111-1111-111111111111"}
						"""))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("There is already a category called \"Groceries\" here");
	}

	@Test
	void renameReturnsTheRenamedCategory() {
		given(service.rename(SNACKS, "Chai & snacks")).willReturn(snacks().withName("Chai & snacks"));

		assertThat(mvc.patch().uri("/api/v1/categories/{id}", SNACKS)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Chai & snacks"}
						"""))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.name")
				.isEqualTo("Chai & snacks");
	}

	@Test
	void renameReturns404ForAnUnknownCategory() {
		given(service.rename(SNACKS, "Anything")).willThrow(new CategoryNotFoundException(SNACKS));

		assertThat(mvc.patch().uri("/api/v1/categories/{id}", SNACKS)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Anything"}
						"""))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void deleteReturns204() {
		assertThat(mvc.delete().uri("/api/v1/categories/{id}", SNACKS)).hasStatus(HttpStatus.NO_CONTENT);

		then(service).should().delete(SNACKS);
	}

	@Test
	void deleteReturns409WhenTheCategoryIsInUse() {
		willThrow(new CategoryInUseException("\"Food\" has sub-categories. Delete or rename those first."))
			.given(service)
			.delete(FOOD);

		assertThat(mvc.delete().uri("/api/v1/categories/{id}", FOOD))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("\"Food\" has sub-categories. Delete or rename those first.");
	}

	private static Category snacks() {
		return new Category(SNACKS, FOOD, "Snacks", CategoryKind.EXPENSE, Instant.parse("2026-10-01T10:00:00Z"));
	}

}
