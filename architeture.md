# Product Catalog 
A Spring Boot application with a simple front end for searching, paginating, adding, and removing products, with 1,000+ preloaded products.  


```
┌──────────────────────────────┐
│          Frontend            │
│      HTML / CSS / JS         │
└──────────────┬───────────────┘
               │ HTTP/JSON
               ▼
┌──────────────────────────────┐
│        Spring Boot           │
│                              │
│ Controller                   │
│     ↓                        │
│ Service                      │
│     ↓                        │
│ Repository                   │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│            H2                │
│        1,000+ products       │
└──────────────────────────────┘
```

# project
tools:
- Java 21
- Spring Boot
- Maven
- Spring Web
- Spring Data JPA
- H2
- Validation
- expections
- Spring Boot Test 
- junit 5
- Mockmvc


struct project:
```
product-catalog/
├── pom.xml
├── README.md
├── .gitignore
├── docs/
│   └── ai/
│       ├── architecture.md
│       ├── implementation.md
│       └── review.md
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com.example.productcatalog/
    │   │       ├── ProductCatalogApplication.java
    │   │       ├── product/
    │   │       │   ├── Product.java
    │   │       │   ├── ProductRepository.java
    │   │       │   ├── ProductService.java
    │   │       │   ├── ProductController.java
    │   │       │   └── dto/
    │   │       │       ├── CreateProductRequest.java
    │   │       │       ├── ProductResponse.java
    │   │       │       └── ProductPageResponse.java
    │   │       ├── exception/
    │   │       │   ├── ProductNotFoundException.java
    │   │       │   └── GlobalExceptionHandler.java
    │   │       └── config/
    │   │           └── DataInitializer.java
    │   └── resources/
    │       ├── application.yml
    │       └── static/
    │           ├── index.html
    │           ├── app.js
    │           └── styles.css
    └── test/
        └── java/
            └── com.example.productcatalog/
                ├── product/
                │   ├── ProductControllerTest.java
                │   └── ProductServiceTest.java
                └── ProductCatalogApplicationTest.java
```

vamos manter o projecto simples e rapido de implmentar e correr
```
mvn spring-boot:run
       ↓
http://localhost:8080
       ↓
Frontend + API
```


# Product
```
Product
├── id
├── name
├── category
└── price
```

Functionalities:
- list products
- search by name
- pagination
- add/delete products
- 1000 products
- simple ui

# Architeture

I dont need a very complex architure, simple monolitic works for this

```
                    ┌───────────────────┐
                    │     Frontend      │
                    │ HTML/CSS/JS       │
                    └─────────┬─────────┘
                              │ HTTP/JSON
                              ▼
                    ┌───────────────────┐
                    │ ProductController │
                    │      REST         │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │   ProductService  │
                    │ Business Logic    │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ ProductRepository │
                    │   Spring Data JPA │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │        H2         │
                    └───────────────────┘
```
### Controller

Responsável por:
- receber requests HTTP;
- validar parâmetros de entrada;
- chamar o service;
- devolver responses HTTP adequadas.

O Controller não deverá conter lógica de negócio significativa.

### Service

Responsável por:
- regras da aplicação;
- criação de produtos;
- pesquisa;
- paginação;
- remoção;  
- tratamento de casos como produto inexistente.

### Repository

Responsável pelo acesso à base de dados através de Spring Data JPA.

### Entity

Representa o modelo persistido na base de dados.

### DTOs

Representam o contrato público da API.

Não expor diretamente a entidade JPA como contrato da API.

# Entity

I will use:
- @Entity
- @GeneratedValue(strategy=GenerationType.IDENTITY)

name will be nullable=false

price will be double

# Repository  

Will be JpaRepository<Product,Long>  

Will support:
- list product by page
- search by name (ex: findbyNameContainingIgnoreCase(String name, Pageable pageable))
- search case sensitive

dont list all product for dont use too much memory

# API REST

## List products

GET
/api/products

query parameters:
- page
- size
- search

Ex:
/api/products?page=2&size=20
/api/products?page=0&size=20&search=mouse

### Response
DTO fields:
- items
- page
- size
- totalElements
- totalPages

EX:
```
{ 
       "items": 
       [ 
              { "id": 1,
              "name": "Wireless Headphones", 
              "category": "Electronics", 
              "price": 79.99 
              } 
       ], 
       "page": 0, 
       "size": 20, 
       "totalElements": 1500, 
       "totalPages": 75 
}
```
## Add product

POST
/api/products

request:
```
{
       name:"mouse",
       price:50.00,
       catalog:"technology"
}
```
Response:
```
201 created
```
body:
```
{
       id:1
       name:"mouse",
       price:50.00,
       catalog:"technology"
}
```

### Validation
CreateProductRequest will use jakarta validation  
Rules:  
name (@NotBlank)  
price (@NotNull,@Positive)  
catalog (@NotBlank)

## delete product

DELETE /api/products/{id}

Ex:

DELETE /api/products/123

Sucess:

204 No Content

Produto inexistente:

404 Not Found

## Error handling

ProductNotFoundException
GlobalExceptionHandler

Ex:

{
  "timestamp": "2026-09-25T20:00:00Z",
  "status": 404,
  "message": "Product not found"
}

Para erros de validação:

{
  "timestamp": "2026-09-25T20:00:00Z",
  "status": 400,
  "message": "Validation failed"
}

# Pagination
dont show 1000 items one time

flow:
```
Browser -> GET /api/products?page=2&size=20 -> Controller -> Service -> Repository -> H2 -> 20 records -> Browser
```

on backend should use Pageable with default values:
- page=0
- size=20

and should have protection for high size value for example limit size 100


# Search

The initial search will be by name only.

Example:

GET /api/products?search=headphones

It should perform a case-insensitive, partial search.

For example:

“head”

should return:

Wireless Headphones
Gaming Headset

if the name contains the search term.

Do not implement full-text search or Elasticsearch.

# Sorting

Initial sort order:

id ASC

# H2

Use H2 as an embedded/in-memory database.

Sample configuration:

spring:
  datasource:
    url: jdbc:h2:mem:catalog
    username: sa
    password:
    driver-class-name: org.h2.Driver

  jpa:
    hibernate:
      ddl-auto: create-drop

Do not rely on PostgreSQL, MySQL, or any other external service to run the application and its simple for this application to use





