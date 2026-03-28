# usuario-service

API REST desarrollada en Spring Boot desplegada en Microsoft Azure, con conectividad a Azure SQL Server a través de una Virtual Network privada.

---

## Tabla de contenidos

- [Descripción general](#descripción-general)
- [Stack tecnológico](#stack-tecnológico)
- [Arquitectura cloud](#arquitectura-cloud)
- [Recursos de Azure](#recursos-de-azure)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Configuración de entornos](#configuración-de-entornos)
- [Docker](#docker)
- [Despliegue en Azure](#despliegue-en-azure)
- [Variables de entorno](#variables-de-entorno)
- [Seguridad de red](#seguridad-de-red)

---

## Descripción general

`usuario-service` es una API REST construida con Spring Boot que gestiona información de usuarios. Está desplegada en Azure Container Apps, conectada a una base de datos Azure SQL Server a través de una Virtual Network privada. La comunicación entre la API y la base de datos viaja por el backbone privado de Microsoft sin salir a internet público.

---

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 17 |
| Framework | Spring Boot |
| ORM | Hibernate / Spring Data JPA |
| Build | Maven |
| Base de datos | SQL Server (local) / Azure SQL Server (nube) |
| Contenedores | Docker (multi-stage build) |
| Registry | Azure Container Registry (ACR) |
| Cómputo | Azure Container Apps |
| Red | Azure Virtual Network |
| CI local | Docker Compose |

---

## Arquitectura cloud

```
Internet (HTTPS)
      │
      ▼
┌─────────────────────────────────────────────────────────────┐
│              Azure Virtual Network (West US 2)              │
│                      10.0.0.0/16                            │
│                                                             │
│  ┌──────────────────────────┐  ┌───────────────────────┐   │
│  │      subnet-app          │  │     subnet-data        │   │
│  │      10.0.1.0/24         │  │     10.0.2.0/24        │   │
│  │                          │  │                        │   │
│  │  NSG: permite HTTPS      │  │  NSG: puerto 1433      │   │
│  │                          │  │  solo desde subnet-app │   │
│  │  Container Apps Env      │  │                        │   │
│  │  (Virtual IP: External)  │  │  SQL Server Firewall   │   │
│  │         │                │  │  (Selected networks)   │   │
│  │  Container App           │  │         │              │   │
│  │  ca-usuario-service      │  │  Azure SQL Server      │   │
│  │         │                │  │  sql-usuario-service   │   │
│  │  Spring Boot API         │  │         │              │   │
│  │  Java 17 · Maven         │  │  SQL Database          │   │
│  │  Hibernate JPA           │  │  usuariodb (free tier) │   │
│  │         │                │  │                        │   │
│  └─────────┼────────────────┘  └───────────┬────────────┘   │
│            │                               │                 │
│            └── backbone Microsoft ─────────┘                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘

Azure Container Registry (externo a VNet)
acrusuarioservice → docker pull → Container App
```

### Flujo de una request

```
Cliente → HTTPS → Container App (subnet-app)
                        │
                        │ puerto 1433 (backbone Microsoft)
                        ▼
                  Azure SQL Server (subnet-data)
                        │
                        ▼
                   usuariodb
```

### Nota sobre conectividad

El Container Apps Environment usa **Virtual IP: External**, lo que significa que el tráfico saliente hacia el SQL Server utiliza IPs del rango de Microsoft. Azure identifica este tráfico implícitamente como interno, permitiendo la comunicación con el SQL Server aunque no exista un Private Endpoint. El tráfico viaja por el backbone privado de Microsoft sin salir a internet público.

> Para un entorno de producción se recomienda usar **Virtual IP: Internal** junto con un **Private Endpoint** en la `subnet-data`, lo que elimina completamente la exposición pública del SQL Server.

---

## Recursos de Azure

| Recurso | Tipo | Descripción |
|---|---|---|
| `rg-usuario-service` | Resource Group | Contenedor lógico de todos los recursos |
| `vnet-usuario-service` | Virtual Network | Red privada — West US 2 — 10.0.0.0/16 |
| `subnet-app` | Subnet | Capa de aplicación — 10.0.1.0/24 |
| `subnet-data` | Subnet | Capa de datos — 10.0.2.0/24 |
| `nsg-subnet-app` | Network Security Group | Reglas de firewall para subnet-app |
| `nsg-subnet-data` | Network Security Group | Reglas de firewall para subnet-data |
| `acrUsuarioService` | Container Registry | Registry privado de imágenes Docker |
| `cae-usuario-service` | Container Apps Environment | Ambiente de ejecución de contenedores |
| `ca-usuario-service` | Container App | API desplegada — puerto 8080 |
| `sql-usuario-service` | SQL Server | Servidor lógico de Azure SQL |
| `usuariodb` | SQL Database | Base de datos — free tier serverless |

---

## Estructura del proyecto

```
usuario-service/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/ejemplo/usuarioservice/
│       │       ├── controller/
│       │       ├── service/
│       │       ├── repository/
│       │       └── model/
│       └── resources/
│           ├── application.properties          # Configuración base
│           ├── application-dev.properties      # Configuración local (git ignored)
│           └── application-prod.properties     # Configuración Azure
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

## Configuración de entornos

El proyecto usa Spring Profiles para separar la configuración local de la de producción.

### `application.properties` — base

```properties
spring.application.name=usuario-service
spring.profiles.active=dev
```

### `application-dev.properties` — local (no subir al repositorio)

```properties
# Base de datos local (SQL Server en Docker)
spring.datasource.url=jdbc:sqlserver://localhost:1433;database=usuariodb;encrypt=false;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=tu_password_local

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.SQLServerDialect
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
```

### `application-prod.properties` — Azure

```properties
# Credenciales inyectadas como variables de entorno desde Container Apps
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.SQLServerDialect
```

### `.gitignore`

```
application-dev.properties
application-prod.properties
.env
```

---

## Docker

### Dockerfile — multi-stage build

```dockerfile
# Etapa 1: compilar
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# Etapa 2: imagen final (solo el JAR)
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

La estrategia multi-stage genera una imagen final que no incluye Maven ni el código fuente, reduciendo significativamente el tamaño de la imagen.

### docker-compose.yml — entorno local completo

```yaml
services:
  sqlserver:
    image: mcr.microsoft.com/mssql/server:2022-latest
    environment:
      ACCEPT_EULA: "Y"
      MSSQL_SA_PASSWORD: "Admin1234!"
    ports:
      - "1433:1433"
    volumes:
      - sqldata:/var/lib/mssql/data
    healthcheck:
      test: ["CMD", "/opt/mssql-tools18/bin/sqlcmd", "-S", "localhost", "-U", "sa", "-P", "Admin1234!", "-Q", "SELECT 1", "-No"]
      interval: 10s
      timeout: 5s
      retries: 5

  api:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: "jdbc:sqlserver://sqlserver:1433;database=usuariodb;encrypt=false;trustServerCertificate=true"
      DB_USERNAME: sa
      DB_PASSWORD: "Admin1234!"
    depends_on:
      sqlserver:
        condition: service_healthy

volumes:
  sqldata:
```

### Comandos útiles

```bash
# Levantar todo el entorno local
docker-compose up

# Solo SQL Server (para correr la API desde el IDE)
docker-compose up sqlserver

# Construir la imagen manualmente
docker build -t usuario-service:latest .

# Correr el contenedor de la API apuntando a SQL Server local
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL="jdbc:sqlserver://host.docker.internal:1433;database=usuariodb;encrypt=false;trustServerCertificate=true" \
  -e DB_USERNAME="sa" \
  -e DB_PASSWORD="tu_password" \
  usuario-service:latest
```

---

## Despliegue en Azure

### Prerrequisitos

- Azure CLI instalado y autenticado (`az login`)
- Docker instalado y corriendo
- Imagen construida localmente (`docker build -t usuario-service:latest .`)

### Pasos

```bash
# 1. Iniciar sesión en Azure Container Registry
az acr login --name acrUsuarioService

# 2. Etiquetar la imagen con el nombre del registry (todo en minúsculas)
docker tag usuario-service:latest acrusuarioservice.azurecr.io/usuario-service:latest

# 3. Subir la imagen al registry
docker push acrusuarioservice.azurecr.io/usuario-service:latest

# 4. Verificar que la imagen está disponible
az acr repository list --name acrUsuarioService --output table
```

### Actualizar la imagen en producción

Cuando hagas cambios en el código:

```bash
# 1. Reconstruir la imagen
docker build -t usuario-service:latest .

# 2. Re-etiquetar y subir
docker tag usuario-service:latest acrusuarioservice.azurecr.io/usuario-service:latest
docker push acrusuarioservice.azurecr.io/usuario-service:latest

# 3. En el Portal de Azure: Container App → Revisions → Create new revision
#    Azure jalará automáticamente la nueva imagen del registry
```

---

## Variables de entorno

Las siguientes variables deben configurarse en **Container Apps → Containers → Environment variables** dentro del Portal de Azure:

| Variable | Descripción | Ejemplo |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Activa el perfil de producción | `prod` |
| `DB_URL` | Cadena de conexión JDBC al SQL Server | `jdbc:sqlserver://sql-usuario-service.database.windows.net:1433;database=usuariodb;encrypt=true;trustServerCertificate=false` |
| `DB_USERNAME` | Usuario administrador del SQL Server | `sqladmin` |
| `DB_PASSWORD` | Contraseña del SQL Server | `*****` |

> Las credenciales nunca deben guardarse en el repositorio. En producción se recomienda integrar Azure Key Vault con Managed Identity para gestionar los secretos de forma segura.

---

## Seguridad de red

### Network Security Groups

| NSG | Regla | Puerto | Origen | Acción |
|---|---|---|---|---|
| `nsg-subnet-app` | Tráfico HTTPS entrante | 443 | Internet | Allow |
| `nsg-subnet-data` | SQL Server desde app | 1433 | 10.0.1.0/24 | Allow |
| `nsg-subnet-data` | Resto del tráfico | * | * | Deny |

### SQL Server Firewall

El SQL Server está configurado con **Public network access: Selected networks**. El acceso está restringido a través del comportamiento implícito del backbone de Microsoft cuando el Container Apps Environment tiene Virtual IP: External.

Para agregar acceso temporal desde una máquina local (solo para desarrollo):

1. Ir a `sql-usuario-service` → **Networking** → **Firewall rules**
2. Agregar la IP pública de la máquina local
3. Eliminar la regla al terminar las pruebas

### Recomendaciones para producción

| Mejora | Descripción |
|---|---|
| Private Endpoint | Asignar IP privada al SQL Server dentro de `subnet-data`, eliminando la exposición pública |
| Virtual IP Internal | Recrear el Container Apps Environment con Virtual IP Internal para que el tráfico saliente use la IP privada de la subnet |
| Azure Key Vault | Almacenar credenciales de base de datos en Key Vault e inyectarlas vía Managed Identity |
| Application Insights | Agregar telemetría y trazas distribuidas |
| Log Analytics | Centralizar logs de todos los recursos |
