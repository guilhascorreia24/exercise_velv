# Product Catalog 
uma aplicação Spring Boot + frontend simples para pesquisar, paginar, adicionar e remover produtos, com 1.000+ produtos pré-carregados.

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

# projecto
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


estrutura:
```
product-catalog/
│
├── pom.xml
├── README.md
├── .gitignore
│
├── src/
│   ├── main/
│   │   ├── java/com/example/catalog/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── static/
│   │           ├── index.html
│   │           ├── app.js
│   │           └── styles.css
│   │
│   └── test/
│       └── java/com/example/catalog/
│
└── docker-compose.yml   # opcional
```

vamos manter o projecto simples e rapido de implmentar e correr
```
mvn spring-boot:run
       ↓
http://localhost:8080
       ↓
Frontend + API
```

