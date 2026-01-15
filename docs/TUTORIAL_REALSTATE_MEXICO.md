# Tutorial Completo: Sistema de Bienes Raíces para México

> **Guía paso a paso para crear una aplicación completa de administración inmobiliaria usando WebGen/LST**
> 
> **Idioma:** Español (Spanish)  
> **Dominio:** Bienes Raíces / Real Estate  
> **País:** México

---

## Tabla de Contenidos

1. [Visión General del Proyecto](#1-visión-general-del-proyecto)
2. [Crear el Proyecto](#2-crear-el-proyecto)
3. [Diseño de la Base de Datos](#3-diseño-de-la-base-de-datos)
4. [Migraciones de Base de Datos](#4-migraciones-de-base-de-datos)
5. [Scaffolding Automático](#5-scaffolding-automático)
6. [Configuración de Entidades](#6-configuración-de-entidades)
7. [Implementación de Hooks](#7-implementación-de-hooks)
8. [Validadores Personalizados](#8-validadores-personalizados)
9. [Internacionalización (Español)](#9-internacionalización-español)
10. [Pruebas y Despliegue](#10-pruebas-y-despliegue)

---

## 1. Visión General del Proyecto

### 1.1 Descripción del Sistema

Sistema de gestión inmobiliaria que permite:
- ✅ Gestión de propiedades (casas, departamentos, terrenos)
- ✅ Registro de clientes (compradores, vendedores, arrendatarios)
- ✅ Seguimiento de transacciones (ventas, rentas)
- ✅ Administración de agentes inmobiliarios
- ✅ Gestión de citas y visitas
- ✅ Catálogo de documentos y contratos
- ✅ Comisiones y pagos
- ✅ Reportes y análisis

### 1.2 Módulos del Sistema

| Módulo | Entidades | Categoría |
|--------|-----------|-----------|
| **Catálogos** | Estados, Municipios, Colonias, Tipos de Propiedad | `:catalog` |
| **Clientes** | Clientes, Agentes | `:clients` |
| **Propiedades** | Propiedades, Características, Fotos | `:properties` |
| **Transacciones** | Ventas, Rentas, Citas | `:transactions` |
| **Finanzas** | Pagos, Comisiones, Avalúos | `:financial` |
| **Documentos** | Documentos, Contratos | `:documents` |
| **Sistema** | Usuarios, Permisos | `:system` |

### 1.3 Tecnologías

- **Backend:** Clojure + Ring + Compojure
- **Base de Datos:** SQLite (desarrollo) / PostgreSQL (producción)
- **Frontend:** Bootstrap 5 + HTMX
- **Template Engine:** Selmer
- **Framework:** WebGen/LST Parameter-Driven

---

## 2. Crear el Proyecto

### 2.1 Generar Proyecto Nuevo

```bash
# Crear proyecto desde template
lein new webgen bienes-raices

# Entrar al directorio
cd bienes-raices

# Verificar estructura
ls -la
```

**Estructura generada:**
```
bienes-raices/
├── src/
│   └── bienes_raices/
│       ├── core.clj
│       ├── engine/
│       ├── hooks/
│       └── validators/
├── resources/
│   ├── entities/
│   ├── migrations/
│   ├── private/
│   │   └── config.clj
│   ├── public/
│   └── i18n/
├── project.clj
└── README.md
```

### 2.2 Configurar Base de Datos

Editar `resources/private/config.clj`:

```clojure
{:connections {
   ;; SQLite para desarrollo
   :sqlite {:db-type "sqlite"
            :db-class "org.sqlite.JDBC"
            :db-name "db/bienes_raices.sqlite"}
   
   ;; PostgreSQL para producción
   :postgres {:db-type "postgresql"
              :db-host "localhost"
              :db-port 5432
              :db-name "bienes_raices_prod"
              :db-user "postgres"
              :db-pwd "tu_password"}
   
   :default :sqlite
   :main :sqlite}
 
 :site-name "Sistema de Bienes Raíces"
 :locale "es-MX"
 :timezone "America/Mexico_City"}
```

---

## 3. Diseño de la Base de Datos

### 3.1 Modelo Entidad-Relación

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│   CLIENTES   │       │  PROPIEDADES │       │   AGENTES    │
├──────────────┤       ├──────────────┤       ├──────────────┤
│ id (PK)      │       │ id (PK)      │       │ id (PK)      │
│ nombre       │       │ titulo       │       │ nombre       │
│ tipo         │◄─────┤│ cliente_id   │       │ email        │
│ email        │       │ agente_id    │├─────►│ telefono     │
│ telefono     │       │ tipo_id      │       │ comision_%   │
│ rfc          │       │ estado_id    │       └──────────────┘
└──────────────┘       │ municipio_id │
       │               │ precio       │
       │               │ status       │
       │               └──────────────┘
       │                      │
       │                      │
       │               ┌──────▼───────┐
       │               │    FOTOS     │
       │               ├──────────────┤
       │               │ id (PK)      │
       │               │ propiedad_id │
       │               │ url          │
       │               └──────────────┘
       │
       │               ┌──────────────┐
       └──────────────►│    VENTAS    │
                       ├──────────────┤
                       │ id (PK)      │
                       │ propiedad_id │
                       │ cliente_id   │
                       │ agente_id    │
                       │ fecha        │
                       │ precio_final │
                       │ comision     │
                       └──────────────┘
```

### 3.2 Catálogos Base

- **Estados** → 32 estados de México
- **Municipios** → Municipios por estado
- **Colonias** → Colonias/barrios
- **Tipos de Propiedad** → Casa, Departamento, Terreno, Local, Oficina
- **Estatus** → Disponible, Vendido, Rentado, Reservado

---

## 4. Migraciones de Base de Datos

### 4.1 Crear Migración Inicial

```bash
lein create-migration 001-esquema-inicial
```

### 4.2 Contenido de la Migración

Archivo: `resources/migrations/001-esquema-inicial.sql`

```sql
-- =====================================================
-- CATÁLOGOS BASE
-- =====================================================

-- Estados de México
CREATE TABLE estados (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  clave VARCHAR(2) NOT NULL UNIQUE,
  nombre VARCHAR(100) NOT NULL,
  activo CHAR(1) DEFAULT 'T'
);

INSERT INTO estados (clave, nombre) VALUES
  ('AG', 'Aguascalientes'),
  ('BC', 'Baja California'),
  ('BS', 'Baja California Sur'),
  ('CM', 'Campeche'),
  ('CS', 'Chiapas'),
  ('CH', 'Chihuahua'),
  ('CX', 'Ciudad de México'),
  ('CO', 'Coahuila'),
  ('CL', 'Colima'),
  ('DG', 'Durango'),
  ('GT', 'Guanajuato'),
  ('GR', 'Guerrero'),
  ('HG', 'Hidalgo'),
  ('JA', 'Jalisco'),
  ('EM', 'Estado de México'),
  ('MI', 'Michoacán'),
  ('MO', 'Morelos'),
  ('NA', 'Nayarit'),
  ('NL', 'Nuevo León'),
  ('OA', 'Oaxaca'),
  ('PU', 'Puebla'),
  ('QT', 'Querétaro'),
  ('QR', 'Quintana Roo'),
  ('SL', 'San Luis Potosí'),
  ('SI', 'Sinaloa'),
  ('SO', 'Sonora'),
  ('TB', 'Tabasco'),
  ('TM', 'Tamaulipas'),
  ('TL', 'Tlaxcala'),
  ('VE', 'Veracruz'),
  ('YU', 'Yucatán'),
  ('ZA', 'Zacatecas');

-- Municipios (ejemplo para algunos estados)
CREATE TABLE municipios (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  estado_id INTEGER NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  activo CHAR(1) DEFAULT 'T',
  FOREIGN KEY (estado_id) REFERENCES estados(id)
);

-- Ejemplos de municipios de Jalisco
INSERT INTO municipios (estado_id, nombre) VALUES
  (14, 'Guadalajara'),
  (14, 'Zapopan'),
  (14, 'Tlaquepaque'),
  (14, 'Tonalá'),
  (14, 'Puerto Vallarta');

-- Colonias/Barrios
CREATE TABLE colonias (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  municipio_id INTEGER NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  codigo_postal VARCHAR(5),
  activo CHAR(1) DEFAULT 'T',
  FOREIGN KEY (municipio_id) REFERENCES municipios(id)
);

-- Tipos de Propiedad
CREATE TABLE tipos_propiedad (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre VARCHAR(50) NOT NULL,
  descripcion TEXT,
  activo CHAR(1) DEFAULT 'T'
);

INSERT INTO tipos_propiedad (nombre, descripcion) VALUES
  ('Casa', 'Casa habitación independiente'),
  ('Departamento', 'Departamento o condominio'),
  ('Terreno', 'Terreno baldío'),
  ('Local Comercial', 'Local para negocio'),
  ('Oficina', 'Oficina o espacio corporativo'),
  ('Bodega', 'Bodega industrial o comercial'),
  ('Rancho', 'Rancho o finca rural'),
  ('Penthouse', 'Departamento de lujo en último piso');

-- =====================================================
-- MÓDULO DE CLIENTES
-- =====================================================

CREATE TABLE clientes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tipo VARCHAR(20) NOT NULL, -- 'Comprador', 'Vendedor', 'Arrendatario', 'Arrendador'
  nombre VARCHAR(100) NOT NULL,
  apellido_paterno VARCHAR(50),
  apellido_materno VARCHAR(50),
  email VARCHAR(100),
  telefono VARCHAR(15),
  celular VARCHAR(15),
  rfc VARCHAR(13),
  curp VARCHAR(18),
  fecha_nacimiento DATE,
  estado_civil VARCHAR(20),
  ocupacion VARCHAR(100),
  
  -- Dirección
  calle VARCHAR(200),
  numero_exterior VARCHAR(10),
  numero_interior VARCHAR(10),
  colonia_id INTEGER,
  codigo_postal VARCHAR(5),
  
  -- Control
  activo CHAR(1) DEFAULT 'T',
  notas TEXT,
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME,
  
  FOREIGN KEY (colonia_id) REFERENCES colonias(id)
);

-- =====================================================
-- MÓDULO DE AGENTES
-- =====================================================

CREATE TABLE agentes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre VARCHAR(100) NOT NULL,
  apellido_paterno VARCHAR(50),
  apellido_materno VARCHAR(50),
  email VARCHAR(100) UNIQUE NOT NULL,
  telefono VARCHAR(15),
  celular VARCHAR(15) NOT NULL,
  
  -- Información profesional
  cedula_profesional VARCHAR(20),
  licencia_inmobiliaria VARCHAR(50),
  porcentaje_comision DECIMAL(5,2) DEFAULT 3.00,
  
  -- Control
  activo CHAR(1) DEFAULT 'T',
  foto_url VARCHAR(255),
  biografia TEXT,
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME
);

-- =====================================================
-- MÓDULO DE PROPIEDADES
-- =====================================================

CREATE TABLE propiedades (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  
  -- Identificación
  clave VARCHAR(20) UNIQUE,
  titulo VARCHAR(200) NOT NULL,
  descripcion TEXT,
  tipo_id INTEGER NOT NULL,
  
  -- Ubicación
  calle VARCHAR(200),
  numero_exterior VARCHAR(10),
  numero_interior VARCHAR(10),
  colonia_id INTEGER,
  municipio_id INTEGER,
  estado_id INTEGER NOT NULL,
  codigo_postal VARCHAR(5),
  latitud DECIMAL(10,8),
  longitud DECIMAL(11,8),
  
  -- Características
  terreno_m2 DECIMAL(10,2),
  construccion_m2 DECIMAL(10,2),
  recamaras INTEGER,
  banos_completos INTEGER,
  medios_banos INTEGER,
  estacionamientos INTEGER,
  niveles INTEGER DEFAULT 1,
  antiguedad_anos INTEGER,
  
  -- Amenidades (Sí/No)
  alberca CHAR(1) DEFAULT 'F',
  jardin CHAR(1) DEFAULT 'F',
  roof_garden CHAR(1) DEFAULT 'F',
  terraza CHAR(1) DEFAULT 'F',
  balcon CHAR(1) DEFAULT 'F',
  cuarto_servicio CHAR(1) DEFAULT 'F',
  gym CHAR(1) DEFAULT 'F',
  seguridad_24h CHAR(1) DEFAULT 'F',
  area_juegos CHAR(1) DEFAULT 'F',
  salon_eventos CHAR(1) DEFAULT 'F',
  
  -- Comercial
  operacion VARCHAR(10) NOT NULL, -- 'Venta', 'Renta', 'Ambos'
  precio_venta DECIMAL(12,2),
  precio_renta DECIMAL(10,2),
  moneda VARCHAR(3) DEFAULT 'MXN',
  status VARCHAR(20) DEFAULT 'Disponible', -- Disponible, Reservada, Vendida, Rentada
  
  -- Relaciones
  cliente_propietario_id INTEGER,
  agente_id INTEGER NOT NULL,
  
  -- Control
  activo CHAR(1) DEFAULT 'T',
  destacada CHAR(1) DEFAULT 'F',
  fecha_registro DATE DEFAULT CURRENT_DATE,
  fecha_publicacion DATE,
  visitas INTEGER DEFAULT 0,
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME,
  
  FOREIGN KEY (tipo_id) REFERENCES tipos_propiedad(id),
  FOREIGN KEY (colonia_id) REFERENCES colonias(id),
  FOREIGN KEY (municipio_id) REFERENCES municipios(id),
  FOREIGN KEY (estado_id) REFERENCES estados(id),
  FOREIGN KEY (cliente_propietario_id) REFERENCES clientes(id),
  FOREIGN KEY (agente_id) REFERENCES agentes(id)
);

-- Fotos de Propiedades
CREATE TABLE fotos_propiedad (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  propiedad_id INTEGER NOT NULL,
  url VARCHAR(255) NOT NULL,
  descripcion VARCHAR(200),
  es_principal CHAR(1) DEFAULT 'F',
  orden INTEGER DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (propiedad_id) REFERENCES propiedades(id) ON DELETE CASCADE
);

-- =====================================================
-- MÓDULO DE TRANSACCIONES
-- =====================================================

-- Ventas
CREATE TABLE ventas (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  propiedad_id INTEGER NOT NULL,
  cliente_comprador_id INTEGER NOT NULL,
  cliente_vendedor_id INTEGER,
  agente_id INTEGER NOT NULL,
  
  fecha_venta DATE NOT NULL,
  precio_venta DECIMAL(12,2) NOT NULL,
  enganche DECIMAL(12,2),
  financiamiento CHAR(1) DEFAULT 'F',
  institucion_financiera VARCHAR(100),
  
  comision_total DECIMAL(10,2),
  comision_agente DECIMAL(10,2),
  
  status VARCHAR(20) DEFAULT 'En Proceso', -- En Proceso, Escriturada, Cancelada
  notas TEXT,
  
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME,
  
  FOREIGN KEY (propiedad_id) REFERENCES propiedades(id),
  FOREIGN KEY (cliente_comprador_id) REFERENCES clientes(id),
  FOREIGN KEY (cliente_vendedor_id) REFERENCES clientes(id),
  FOREIGN KEY (agente_id) REFERENCES agentes(id)
);

-- Rentas
CREATE TABLE rentas (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  propiedad_id INTEGER NOT NULL,
  cliente_arrendatario_id INTEGER NOT NULL,
  cliente_arrendador_id INTEGER,
  agente_id INTEGER NOT NULL,
  
  fecha_inicio DATE NOT NULL,
  fecha_fin DATE NOT NULL,
  renta_mensual DECIMAL(10,2) NOT NULL,
  deposito DECIMAL(10,2),
  dia_pago INTEGER DEFAULT 1,
  
  status VARCHAR(20) DEFAULT 'Activa', -- Activa, Finalizada, Cancelada
  notas TEXT,
  
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME,
  
  FOREIGN KEY (propiedad_id) REFERENCES propiedades(id),
  FOREIGN KEY (cliente_arrendatario_id) REFERENCES clientes(id),
  FOREIGN KEY (cliente_arrendador_id) REFERENCES clientes(id),
  FOREIGN KEY (agente_id) REFERENCES agentes(id)
);

-- Citas/Visitas
CREATE TABLE citas (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  propiedad_id INTEGER NOT NULL,
  cliente_id INTEGER NOT NULL,
  agente_id INTEGER NOT NULL,
  
  fecha_cita DATETIME NOT NULL,
  duracion_minutos INTEGER DEFAULT 60,
  tipo VARCHAR(20) DEFAULT 'Visita', -- Visita, Llamada, Virtual
  
  status VARCHAR(20) DEFAULT 'Programada', -- Programada, Completada, Cancelada, NoAsistió
  notas TEXT,
  resultado TEXT, -- Comentarios post-visita
  
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME,
  
  FOREIGN KEY (propiedad_id) REFERENCES propiedades(id),
  FOREIGN KEY (cliente_id) REFERENCES clientes(id),
  FOREIGN KEY (agente_id) REFERENCES agentes(id)
);

-- =====================================================
-- MÓDULO FINANCIERO
-- =====================================================

-- Pagos
CREATE TABLE pagos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tipo VARCHAR(20) NOT NULL, -- 'Renta', 'Venta', 'Comision'
  referencia_id INTEGER, -- ID de venta o renta
  cliente_id INTEGER,
  agente_id INTEGER,
  
  fecha_pago DATE NOT NULL,
  monto DECIMAL(10,2) NOT NULL,
  metodo_pago VARCHAR(20), -- Efectivo, Transferencia, Cheque, Tarjeta
  referencia VARCHAR(100),
  
  concepto TEXT,
  notas TEXT,
  
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  
  FOREIGN KEY (cliente_id) REFERENCES clientes(id),
  FOREIGN KEY (agente_id) REFERENCES agentes(id)
);

-- Avalúos
CREATE TABLE avaluos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  propiedad_id INTEGER NOT NULL,
  
  fecha_avaluo DATE NOT NULL,
  perito_valuador VARCHAR(100),
  institucion VARCHAR(100),
  valor_avaluo DECIMAL(12,2) NOT NULL,
  
  documento_url VARCHAR(255),
  notas TEXT,
  
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  
  FOREIGN KEY (propiedad_id) REFERENCES propiedades(id)
);

-- =====================================================
-- MÓDULO DE DOCUMENTOS
-- =====================================================

CREATE TABLE documentos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tipo VARCHAR(50) NOT NULL, -- Contrato, Escritura, INE, RFC, etc
  entidad VARCHAR(20), -- 'Propiedad', 'Cliente', 'Venta', 'Renta'
  entidad_id INTEGER,
  
  titulo VARCHAR(200) NOT NULL,
  descripcion TEXT,
  archivo_url VARCHAR(255) NOT NULL,
  tipo_archivo VARCHAR(10), -- PDF, DOC, JPG, etc
  tamano_kb INTEGER,
  
  fecha_documento DATE,
  vigencia DATE,
  
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME
);

-- =====================================================
-- MÓDULO DE SISTEMA
-- =====================================================

CREATE TABLE usuarios (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username VARCHAR(50) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  email VARCHAR(100) UNIQUE NOT NULL,
  
  nombre VARCHAR(100) NOT NULL,
  nivel VARCHAR(1) DEFAULT 'U', -- U=Usuario, A=Admin, S=Sistema
  
  agente_id INTEGER, -- Vinculado a un agente
  
  activo CHAR(1) DEFAULT 'T',
  ultimo_acceso DATETIME,
  
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME,
  
  FOREIGN KEY (agente_id) REFERENCES agentes(id)
);

-- Usuario admin inicial
INSERT INTO usuarios (username, password, email, nombre, nivel) VALUES
  ('admin', 'admin123', 'admin@bienesraices.mx', 'Administrador', 'S');

-- =====================================================
-- ÍNDICES
-- =====================================================

CREATE INDEX idx_propiedades_estado ON propiedades(estado_id);
CREATE INDEX idx_propiedades_municipio ON propiedades(municipio_id);
CREATE INDEX idx_propiedades_tipo ON propiedades(tipo_id);
CREATE INDEX idx_propiedades_status ON propiedades(status);
CREATE INDEX idx_propiedades_operacion ON propiedades(operacion);
CREATE INDEX idx_propiedades_agente ON propiedades(agente_id);

CREATE INDEX idx_clientes_tipo ON clientes(tipo);
CREATE INDEX idx_clientes_email ON clientes(email);

CREATE INDEX idx_ventas_fecha ON ventas(fecha_venta);
CREATE INDEX idx_rentas_fecha_inicio ON rentas(fecha_inicio);
CREATE INDEX idx_citas_fecha ON citas(fecha_cita);
CREATE INDEX idx_citas_status ON citas(status);

-- =====================================================
-- FIN DE MIGRACIÓN
-- =====================================================
```

### 4.3 Ejecutar Migración

```bash
# Aplicar migración
lein migrate

# Verificar tablas creadas
sqlite3 db/bienes_raices.sqlite ".tables"
```

---

## 5. Scaffolding Automático

### 5.1 Scaffolding de Catálogos

```bash
# Generar entidades para catálogos
lein scaffold estados
lein scaffold municipios
lein scaffold colonias
lein scaffold tipos_propiedad
```

### 5.2 Scaffolding de Módulos Principales

```bash
# Clientes y Agentes
lein scaffold clientes
lein scaffold agentes

# Propiedades
lein scaffold propiedades
lein scaffold fotos_propiedad

# Transacciones
lein scaffold ventas
lein scaffold rentas
lein scaffold citas

# Finanzas
lein scaffold pagos
lein scaffold avaluos

# Documentos
lein scaffold documentos

# Sistema
lein scaffold usuarios
```

**Resultado:** Se crean archivos EDN en `resources/entities/` para cada tabla.

---

## 6. Configuración de Entidades

### 6.1 Configurar Entidad: Propiedades

Archivo: `resources/entities/propiedades.edn`

```clojure
{:entity :propiedades
 :title "Propiedades"
 :table "propiedades"
 :connection :default
 :rights ["U" "A" "S"]
 :mode :parameter-driven
 :audit? true
 
 :menu-category :properties
 :menu-order 1
 :menu-icon "bi bi-house-door"
 
 :fields [
   {:id :id :type :hidden}
   
   ;; Identificación
   {:id :clave :label "Clave" :type :text :required true :maxlength 20}
   {:id :titulo :label "Título" :type :text :required true :maxlength 200}
   {:id :descripcion :label "Descripción" :type :textarea :rows 5}
   
   {:id :tipo_id 
    :label "Tipo de Propiedad" 
    :type :select 
    :required true
    :options []} ; Se llena dinámicamente
   
   ;; Ubicación
   {:id :estado_id 
    :label "Estado" 
    :type :select 
    :required true
    :options []}
   
   {:id :municipio_id 
    :label "Municipio" 
    :type :select 
    :options []}
   
   {:id :colonia_id 
    :label "Colonia" 
    :type :select 
    :options []}
   
   {:id :calle :label "Calle" :type :text :maxlength 200}
   {:id :numero_exterior :label "Núm. Exterior" :type :text :maxlength 10}
   {:id :numero_interior :label "Núm. Interior" :type :text :maxlength 10}
   {:id :codigo_postal :label "C.P." :type :text :maxlength 5}
   
   ;; Características
   {:id :terreno_m2 :label "Terreno (m²)" :type :decimal :min 0}
   {:id :construccion_m2 :label "Construcción (m²)" :type :decimal :min 0}
   {:id :recamaras :label "Recámaras" :type :number :min 0}
   {:id :banos_completos :label "Baños Completos" :type :number :min 0}
   {:id :medios_banos :label "Medios Baños" :type :number :min 0}
   {:id :estacionamientos :label "Estacionamientos" :type :number :min 0}
   {:id :niveles :label "Niveles" :type :number :min 1 :value 1}
   {:id :antiguedad_anos :label "Antigüedad (años)" :type :number :min 0}
   
   ;; Amenidades
   {:id :alberca :label "Alberca" :type :checkbox :value "F"}
   {:id :jardin :label "Jardín" :type :checkbox :value "F"}
   {:id :roof_garden :label "Roof Garden" :type :checkbox :value "F"}
   {:id :terraza :label "Terraza" :type :checkbox :value "F"}
   {:id :gym :label "Gimnasio" :type :checkbox :value "F"}
   {:id :seguridad_24h :label "Seguridad 24h" :type :checkbox :value "F"}
   
   ;; Comercial
   {:id :operacion 
    :label "Operación" 
    :type :select 
    :required true
    :value "Venta"
    :options [{:value "Venta" :label "Venta"}
              {:value "Renta" :label "Renta"}
              {:value "Ambos" :label "Venta y Renta"}]}
   
   {:id :precio_venta :label "Precio de Venta" :type :decimal :min 0}
   {:id :precio_renta :label "Precio de Renta Mensual" :type :decimal :min 0}
   {:id :moneda :label "Moneda" :type :select :value "MXN"
    :options [{:value "MXN" :label "MXN - Peso Mexicano"}
              {:value "USD" :label "USD - Dólar"}]}
   
   {:id :status 
    :label "Estatus" 
    :type :select 
    :value "Disponible"
    :options [{:value "Disponible" :label "Disponible"}
              {:value "Reservada" :label "Reservada"}
              {:value "Vendida" :label "Vendida"}
              {:value "Rentada" :label "Rentada"}]}
   
   {:id :agente_id 
    :label "Agente Responsable" 
    :type :select 
    :required true
    :options []}
   
   {:id :cliente_propietario_id 
    :label "Propietario" 
    :type :select 
    :options []}
   
   {:id :destacada :label "Propiedad Destacada" :type :checkbox :value "F"}
   {:id :activo :label "Activo" :type :checkbox :value "T"}
   
   ;; Campos de solo lectura
   {:id :visitas :label "Visitas" :type :number :hidden-in-form? true}
   {:id :fecha_registro :label "Fecha Registro" :type :date :hidden-in-form? true}]
 
 :queries {
   :list "SELECT p.*, 
                 tp.nombre as tipo_nombre,
                 e.nombre as estado_nombre,
                 m.nombre as municipio_nombre,
                 a.nombre as agente_nombre
          FROM propiedades p
          LEFT JOIN tipos_propiedad tp ON p.tipo_id = tp.id
          LEFT JOIN estados e ON p.estado_id = e.id
          LEFT JOIN municipios m ON p.municipio_id = m.id
          LEFT JOIN agentes a ON p.agente_id = a.id
          WHERE p.activo = 'T'
          ORDER BY p.fecha_registro DESC"
   
   :get "SELECT * FROM propiedades WHERE id = ?"}
 
 :actions {:new true :edit true :delete true}
 
 :hooks {
   :before-load :bienes_raices.hooks.propiedades/cargar-opciones
   :before-save :bienes_raices.hooks.propiedades/validar-propiedad
   :after-save :bienes_raices.hooks.propiedades/generar-clave
   :before-delete :bienes_raices.hooks.propiedades/verificar-transacciones}
 
 :subgrids [
   {:entity :fotos_propiedad
    :foreign-key :propiedad_id
    :title "Fotos"
    :icon "bi bi-images"}
   
   {:entity :avaluos
    :foreign-key :propiedad_id
    :title "Avalúos"
    :icon "bi bi-graph-up"}
   
   {:entity :citas
    :foreign-key :propiedad_id
    :title "Citas/Visitas"
    :icon "bi bi-calendar-event"}]}
```

### 6.2 Configurar Entidad: Clientes

Archivo: `resources/entities/clientes.edn`

```clojure
{:entity :clientes
 :title "Clientes"
 :table "clientes"
 :connection :default
 :rights ["U" "A" "S"]
 :mode :parameter-driven
 :audit? true
 
 :menu-category :clients
 :menu-order 1
 :menu-icon "bi bi-people"
 
 :fields [
   {:id :id :type :hidden}
   
   {:id :tipo 
    :label "Tipo de Cliente" 
    :type :select 
    :required true
    :options [{:value "Comprador" :label "Comprador"}
              {:value "Vendedor" :label "Vendedor"}
              {:value "Arrendatario" :label "Arrendatario"}
              {:value "Arrendador" :label "Arrendador"}]}
   
   {:id :nombre :label "Nombre(s)" :type :text :required true :maxlength 100}
   {:id :apellido_paterno :label "Apellido Paterno" :type :text :maxlength 50}
   {:id :apellido_materno :label "Apellido Materno" :type :text :maxlength 50}
   
   {:id :email :label "Email" :type :email :maxlength 100}
   {:id :telefono :label "Teléfono" :type :text :maxlength 15 :placeholder "33-1234-5678"}
   {:id :celular :label "Celular" :type :text :maxlength 15 :placeholder "33-1234-5678"}
   
   {:id :rfc :label "RFC" :type :text :maxlength 13 :placeholder "XAXX010101000"}
   {:id :curp :label "CURP" :type :text :maxlength 18 :placeholder "XAXX010101HDFXXX00"}
   {:id :fecha_nacimiento :label "Fecha de Nacimiento" :type :date}
   
   {:id :estado_civil 
    :label "Estado Civil" 
    :type :select
    :options [{:value "Soltero(a)" :label "Soltero(a)"}
              {:value "Casado(a)" :label "Casado(a)"}
              {:value "Divorciado(a)" :label "Divorciado(a)"}
              {:value "Viudo(a)" :label "Viudo(a)"}
              {:value "Unión Libre" :label "Unión Libre"}]}
   
   {:id :ocupacion :label "Ocupación" :type :text :maxlength 100}
   
   ;; Dirección
   {:id :calle :label "Calle" :type :text :maxlength 200}
   {:id :numero_exterior :label "Núm. Exterior" :type :text :maxlength 10}
   {:id :numero_interior :label "Núm. Interior" :type :text :maxlength 10}
   {:id :colonia_id :label "Colonia" :type :select :options []}
   {:id :codigo_postal :label "C.P." :type :text :maxlength 5}
   
   {:id :notas :label "Notas" :type :textarea :rows 4 :hidden-in-grid? true}
   {:id :activo :label "Activo" :type :checkbox :value "T"}
   
   ;; Nombre completo computado
   {:id :nombre_completo :label "Nombre Completo" :type :text :grid-only? true}]
 
 :queries {
   :list "SELECT c.*,
                 co.nombre as colonia_nombre
          FROM clientes c
          LEFT JOIN colonias co ON c.colonia_id = co.id
          WHERE c.activo = 'T'
          ORDER BY c.apellido_paterno, c.apellido_materno, c.nombre"
   
   :get "SELECT * FROM clientes WHERE id = ?"}
 
 :actions {:new true :edit true :delete true}
 
 :hooks {
   :after-load :bienes_raices.hooks.clientes/nombre-completo
   :before-save :bienes_raices.hooks.clientes/validar-cliente}
 
 :subgrids [
   {:entity :documentos
    :foreign-key :entidad_id
    :title "Documentos"
    :icon "bi bi-file-earmark"}]}
```

### 6.3 Configurar Entidad: Agentes

Archivo: `resources/entities/agentes.edn`

```clojure
{:entity :agentes
 :title "Agentes Inmobiliarios"
 :table "agentes"
 :connection :default
 :rights ["A" "S"]
 :mode :parameter-driven
 :audit? true
 
 :menu-category :clients
 :menu-order 2
 :menu-icon "bi bi-person-badge"
 
 :fields [
   {:id :id :type :hidden}
   
   {:id :nombre :label "Nombre(s)" :type :text :required true :maxlength 100}
   {:id :apellido_paterno :label "Apellido Paterno" :type :text :maxlength 50}
   {:id :apellido_materno :label "Apellido Materno" :type :text :maxlength 50}
   
   {:id :email :label "Email" :type :email :required true :maxlength 100}
   {:id :telefono :label "Teléfono" :type :text :maxlength 15}
   {:id :celular :label "Celular" :type :text :required true :maxlength 15}
   
   {:id :cedula_profesional :label "Cédula Profesional" :type :text :maxlength 20}
   {:id :licencia_inmobiliaria :label "Licencia Inmobiliaria" :type :text :maxlength 50}
   {:id :porcentaje_comision :label "% Comisión" :type :decimal :min 0 :max 100 :value 3.00}
   
   {:id :biografia :label "Biografía" :type :textarea :rows 5 :hidden-in-grid? true}
   {:id :foto_url :label "URL Foto" :type :text :maxlength 255 :hidden-in-grid? true}
   {:id :activo :label "Activo" :type :checkbox :value "T"}
   
   {:id :nombre_completo :label "Nombre Completo" :type :text :grid-only? true}]
 
 :queries {
   :list "SELECT * FROM agentes WHERE activo = 'T' ORDER BY apellido_paterno"
   :get "SELECT * FROM agentes WHERE id = ?"}
 
 :actions {:new true :edit true :delete false}
 
 :hooks {
   :after-load :bienes_raices.hooks.agentes/nombre-completo}
 
 :subgrids [
   {:entity :propiedades
    :foreign-key :agente_id
    :title "Propiedades Asignadas"
    :icon "bi bi-house"}
   
   {:entity :ventas
    :foreign-key :agente_id
    :title "Ventas"
    :icon "bi bi-cash-stack"}
   
   {:entity :rentas
    :foreign-key :agente_id
    :title "Rentas"
    :icon "bi bi-key"}]}
```

### 6.4 Configurar Entidades de Catálogo

**Estados:** `resources/entities/estados.edn`

```clojure
{:entity :estados
 :title "Estados"
 :table "estados"
 :menu-category :catalog
 :menu-order 1
 :rights ["A" "S"]
 
 :fields [
   {:id :id :type :hidden}
   {:id :clave :label "Clave" :type :text :required true :maxlength 2}
   {:id :nombre :label "Nombre" :type :text :required true :maxlength 100}
   {:id :activo :label "Activo" :type :checkbox :value "T"}]
 
 :actions {:new true :edit true :delete false}
 
 :subgrids [
   {:entity :municipios
    :foreign-key :estado_id
    :title "Municipios"
    :icon "bi bi-map"}]}
```

**Tipos de Propiedad:** `resources/entities/tipos_propiedad.edn`

```clojure
{:entity :tipos_propiedad
 :title "Tipos de Propiedad"
 :table "tipos_propiedad"
 :menu-category :catalog
 :menu-order 4
 :rights ["A" "S"]
 
 :fields [
   {:id :id :type :hidden}
   {:id :nombre :label "Nombre" :type :text :required true :maxlength 50}
   {:id :descripcion :label "Descripción" :type :textarea :rows 3}
   {:id :activo :label "Activo" :type :checkbox :value "T"}]
 
 :actions {:new true :edit true :delete false}}
```

---

## 7. Implementación de Hooks

### 7.1 Hooks para Propiedades

Archivo: `src/bienes_raices/hooks/propiedades.clj`

```clojure
(ns bienes-raices.hooks.propiedades
  (:require [bienes-raices.engine.crud :as crud]
            [clojure.string :as str]))

;; =============================================================================
;; BEFORE-LOAD: Cargar opciones para selects
;; =============================================================================

(defn cargar-opciones
  "Carga dinámicamente las opciones para los campos select"
  [params]
  ;; Este hook puede modificar params si necesita filtrar
  ;; Por ahora solo retorna params sin cambios
  params)

;; =============================================================================
;; BEFORE-SAVE: Validaciones
;; =============================================================================

(defn validar-propiedad
  "Valida reglas de negocio antes de guardar"
  [params]
  (let [operacion (:operacion params)
        precio-venta (:precio_venta params)
        precio-renta (:precio_renta params)]
    
    (cond
      ;; Si operación es Venta, debe tener precio de venta
      (and (= operacion "Venta") (or (nil? precio-venta) (<= precio-venta 0)))
      {:errors {:precio_venta "Debe especificar precio de venta"}}
      
      ;; Si operación es Renta, debe tener precio de renta
      (and (= operacion "Renta") (or (nil? precio-renta) (<= precio-renta 0)))
      {:errors {:precio_renta "Debe especificar precio de renta mensual"}}
      
      ;; Si operación es Ambos, debe tener ambos precios
      (and (= operacion "Ambos")
           (or (nil? precio-venta) (<= precio-venta 0)
               (nil? precio-renta) (<= precio-renta 0)))
      {:errors {:general "Debe especificar precio de venta Y renta"}}
      
      ;; Construcción no puede ser mayor que terreno
      (and (:construccion_m2 params) (:terreno_m2 params)
           (> (:construccion_m2 params) (:terreno_m2 params)))
      {:errors {:construccion_m2 "No puede ser mayor que el terreno"}}
      
      ;; Todo OK
      :else params)))

;; =============================================================================
;; AFTER-SAVE: Generar clave única
;; =============================================================================

(defn generar-clave
  "Genera clave única para la propiedad si no tiene"
  [entity-id params]
  (when-not (:clave params)
    (let [tipo-id (:tipo_id params)
          estado-id (:estado_id params)
          ;; Obtener abreviatura del tipo y estado
          tipo-abrev (-> (crud/Query ["SELECT nombre FROM tipos_propiedad WHERE id = ?" tipo-id])
                         first
                         :nombre
                         (str/upper-case)
                         (subs 0 3))
          estado-abrev (-> (crud/Query ["SELECT clave FROM estados WHERE id = ?" estado-id])
                          first
                          :clave)
          ;; Generar clave: TIPO-ESTADO-ID (ej: CAS-JA-00123)
          clave (format "%s-%s-%05d" tipo-abrev estado-abrev entity-id)]
      
      ;; Actualizar la clave
      (crud/Execute! ["UPDATE propiedades SET clave = ? WHERE id = ?" clave entity-id])))
  
  {:success true :message "Propiedad guardada exitosamente"})

;; =============================================================================
;; BEFORE-DELETE: Verificar transacciones
;; =============================================================================

(defn verificar-transacciones
  "No permite borrar propiedad con ventas o rentas"
  [entity-id]
  (let [ventas (crud/Query ["SELECT COUNT(*) as cnt FROM ventas WHERE propiedad_id = ?" entity-id])
        rentas (crud/Query ["SELECT COUNT(*) as cnt FROM rentas WHERE propiedad_id = ?" entity-id])
        cnt-ventas (get-in ventas [0 :cnt] 0)
        cnt-rentas (get-in rentas [0 :cnt] 0)]
    
    (cond
      (> cnt-ventas 0)
      {:errors {:general "No se puede eliminar: tiene ventas registradas"}}
      
      (> cnt-rentas 0)
      {:errors {:general "No se puede eliminar: tiene rentas registradas"}}
      
      :else
      {:success true})))
```

### 7.2 Hooks para Clientes

Archivo: `src/bienes_raices/hooks/clientes.clj`

```clojure
(ns bienes-raices.hooks.clientes
  (:require [clojure.string :as str]))

;; =============================================================================
;; AFTER-LOAD: Agregar nombre completo
;; =============================================================================

(defn nombre-completo
  "Agrega campo computado con nombre completo"
  [rows params]
  (mapv (fn [row]
          (let [nombre (str/trim (str (:nombre row) " "
                                      (:apellido_paterno row) " "
                                      (:apellido_materno row)))]
            (assoc row :nombre_completo nombre)))
        rows))

;; =============================================================================
;; BEFORE-SAVE: Validaciones
;; =============================================================================

(defn validar-cliente
  "Valida datos del cliente"
  [params]
  (let [email (:email params)
        celular (:celular params)
        rfc (:rfc params)]
    
    (cond
      ;; Email debe ser único si se proporciona
      (and email (not (re-matches #".+@.+\..+" email)))
      {:errors {:email "Formato de email inválido"}}
      
      ;; RFC debe tener 12 o 13 caracteres
      (and rfc (not (<= 12 (count rfc) 13)))
      {:errors {:rfc "RFC debe tener 12 o 13 caracteres"}}
      
      ;; RFC solo letras y números
      (and rfc (not (re-matches #"[A-Z0-9]+" (str/upper-case rfc))))
      {:errors {:rfc "RFC solo debe contener letras y números"}}
      
      :else
      (-> params
          (update :rfc #(when % (str/upper-case %)))
          (update :curp #(when % (str/upper-case %)))))))
```

### 7.3 Hooks para Agentes

Archivo: `src/bienes_raices/hooks/agentes.clj`

```clojure
(ns bienes-raices.hooks.agentes
  (:require [clojure.string :as str]))

(defn nombre-completo
  "Agrega nombre completo del agente"
  [rows params]
  (mapv (fn [row]
          (let [nombre (str/trim (str (:nombre row) " "
                                      (:apellido_paterno row) " "
                                      (:apellido_materno row)))]
            (assoc row :nombre_completo nombre)))
        rows))
```

---

## 8. Validadores Personalizados

### 8.1 Validadores Comunes

Archivo: `src/bienes_raices/validators/common.clj`

```clojure
(ns bienes-raices.validators.common
  (:require [clojure.string :as str]))

;; =============================================================================
;; RFC (Registro Federal de Contribuyentes)
;; =============================================================================

(defn rfc-valido?
  "Valida formato de RFC mexicano (12 o 13 caracteres)"
  [value _]
  (when value
    (let [rfc (str/upper-case (str/trim (str value)))]
      (and (<= 12 (count rfc) 13)
           (re-matches #"[A-Z]{3,4}\d{6}[A-Z0-9]{2,3}" rfc)))))

;; =============================================================================
;; CURP (Clave Única de Registro de Población)
;; =============================================================================

(defn curp-valida?
  "Valida formato de CURP (18 caracteres)"
  [value _]
  (when value
    (let [curp (str/upper-case (str/trim (str value)))]
      (and (= 18 (count curp))
           (re-matches #"[A-Z]{4}\d{6}[HM][A-Z]{5}[A-Z0-9]\d" curp)))))

;; =============================================================================
;; Código Postal
;; =============================================================================

(defn codigo-postal-valido?
  "Valida CP de México (5 dígitos)"
  [value _]
  (when value
    (re-matches #"\d{5}" (str value))))

;; =============================================================================
;; Teléfono/Celular
;; =============================================================================

(defn telefono-valido?
  "Valida formato de teléfono mexicano"
  [value _]
  (when value
    (let [tel (str/replace (str value) #"[-\s()]" "")]
      (and (<= 10 (count tel) 15)
           (re-matches #"\d+" tel)))))

;; =============================================================================
;; Precio/Monto
;; =============================================================================

(defn precio-positivo?
  "Valida que el precio sea positivo"
  [value _]
  (and value (> value 0)))

(defn precio-razonable?
  "Valida rango razonable para precios inmobiliarios"
  [value _]
  (and value 
       (>= value 10000) ; Mínimo $10,000 MXN
       (<= value 1000000000))) ; Máximo $1,000 millones

;; =============================================================================
;; Porcentaje
;; =============================================================================

(defn porcentaje-valido?
  "Valida que esté entre 0 y 100"
  [value _]
  (and value (>= value 0) (<= value 100)))

;; =============================================================================
;; Metros Cuadrados
;; =============================================================================

(defn m2-valido?
  "Valida metros cuadrados razonables"
  [value _]
  (and value 
       (>= value 1) ; Mínimo 1 m²
       (<= value 100000))) ; Máximo 100,000 m² (10 hectáreas)
```

### 8.2 Aplicar Validadores

Actualizar `propiedades.edn`:

```clojure
{:id :precio_venta 
 :label "Precio de Venta" 
 :type :decimal 
 :min 0
 :validation :bienes-raices.validators.common/precio-razonable?}

{:id :codigo_postal 
 :label "C.P." 
 :type :text 
 :maxlength 5
 :validation :bienes-raices.validators.common/codigo-postal-valido?}
```

Actualizar `clientes.edn`:

```clojure
{:id :rfc 
 :label "RFC" 
 :type :text 
 :maxlength 13
 :validation :bienes-raices.validators.common/rfc-valido?}

{:id :curp 
 :label "CURP" 
 :type :text 
 :maxlength 18
 :validation :bienes-raices.validators.common/curp-valida?}
```

---

## 9. Internacionalización (Español)

### 9.1 Archivo de Traducción

Archivo: `resources/i18n/es.edn`

```clojure
{;; Menú
 :menu/catalog "Catálogos"
 :menu/clients "Clientes"
 :menu/properties "Propiedades"
 :menu/transactions "Transacciones"
 :menu/financial "Finanzas"
 :menu/documents "Documentos"
 :menu/system "Sistema"
 :menu/reports "Reportes"
 
 ;; Acciones
 :action/new "Nuevo"
 :action/edit "Editar"
 :action/delete "Eliminar"
 :action/save "Guardar"
 :action/cancel "Cancelar"
 :action/search "Buscar"
 :action/export "Exportar"
 :action/print "Imprimir"
 
 ;; Mensajes
 :msg/saved "Registro guardado exitosamente"
 :msg/deleted "Registro eliminado"
 :msg/error "Ocurrió un error"
 :msg/confirm-delete "¿Está seguro de eliminar este registro?"
 :msg/no-data "No hay datos para mostrar"
 
 ;; Campos comunes
 :field/id "ID"
 :field/name "Nombre"
 :field/email "Correo Electrónico"
 :field/phone "Teléfono"
 :field/address "Dirección"
 :field/status "Estatus"
 :field/date "Fecha"
 :field/notes "Notas"
 :field/active "Activo"
 
 ;; Propiedades
 :property/title "Título"
 :property/type "Tipo de Propiedad"
 :property/price "Precio"
 :property/bedrooms "Recámaras"
 :property/bathrooms "Baños"
 :property/sqm "Metros Cuadrados"
 :property/location "Ubicación"
 
 ;; Estados
 :status/available "Disponible"
 :status/reserved "Reservada"
 :status/sold "Vendida"
 :status/rented "Rentada"
 
 ;; Tipos de operación
 :operation/sale "Venta"
 :operation/rent "Renta"
 :operation/both "Venta y Renta"}
```

### 9.2 Configurar Locale

En `resources/private/config.clj`:

```clojure
{:locale "es-MX"
 :date-format "dd/MM/yyyy"
 :currency "MXN"
 :currency-symbol "$"
 :decimal-separator "."
 :thousands-separator ","}
```

---

## 10. Pruebas y Despliegue

### 10.1 Iniciar Servidor de Desarrollo

```bash
# Iniciar con auto-reload
lein with-profile dev run

# Abrir navegador
open http://localhost:3000
```

### 10.2 Verificar Funcionalidad

**Checklist:**
- [ ] Login con usuario admin
- [ ] Navegar menú de catálogos
- [ ] Crear nuevo estado
- [ ] Crear nuevo tipo de propiedad
- [ ] Registrar agente
- [ ] Registrar cliente
- [ ] Crear propiedad con todas las características
- [ ] Subir fotos de propiedad
- [ ] Verificar validaciones (RFC, CURP, precios)
- [ ] Crear cita para visita
- [ ] Registrar venta
- [ ] Generar reporte

### 10.3 Build para Producción

```bash
# Compilar uberjar
lein uberjar

# Resultado en:
# target/uberjar/bienes-raices-0.1.0-standalone.jar
```

### 10.4 Desplegar en Servidor

```bash
# Copiar a servidor
scp target/uberjar/bienes-raices-0.1.0-standalone.jar user@server:/opt/app/

# En el servidor
cd /opt/app
java -jar bienes-raices-0.1.0-standalone.jar 8080

# Con systemd (crear servicio)
sudo nano /etc/systemd/system/bienes-raices.service
```

**Contenido del servicio:**

```ini
[Unit]
Description=Sistema de Bienes Raices
After=network.target

[Service]
Type=simple
User=appuser
WorkingDirectory=/opt/app
ExecStart=/usr/bin/java -jar /opt/app/bienes-raices-0.1.0-standalone.jar 8080
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
# Habilitar y arrancar
sudo systemctl enable bienes-raices
sudo systemctl start bienes-raices
sudo systemctl status bienes-raices
```

### 10.5 Configurar Nginx (Reverse Proxy)

```nginx
server {
    listen 80;
    server_name bienesraices.example.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## Resumen de Comandos

```bash
# 1. Crear proyecto
lein new webgen bienes-raices
cd bienes-raices

# 2. Crear migración
lein create-migration 001-esquema-inicial
# (editar SQL)
lein migrate

# 3. Scaffolding
lein scaffold --all

# 4. Desarrollo
lein with-profile dev run

# 5. Producción
lein uberjar
java -jar target/uberjar/bienes-raices-0.1.0-standalone.jar 8080
```

---

## Próximos Pasos

1. **Implementar Dashboard** - Métricas y KPIs
2. **Reportes Avanzados** - PDF, Excel
3. **API REST** - Para mobile app
4. **Geolocalización** - Mapa de propiedades
5. **Búsqueda Avanzada** - Filtros múltiples
6. **Sistema de Notificaciones** - Email/SMS
7. **Multi-tenant** - Para franquicias

---

## Recursos Adicionales

- **CHEATSHEET.md** - Referencia rápida
- **HOOKS_GUIDE.md** - Guía de hooks
- **FRAMEWORK_GUIDE.md** - Documentación completa
- **QUICKSTART.md** - Inicio rápido

---

**🎉 ¡Felicidades! Has creado un sistema completo de bienes raíces en México con WebGen/LST**
