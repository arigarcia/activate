Activate Persistence Framework

[README-PTBR](https://github.com/fwbrasil/activate/blob/master/README-PTBR.md "README-PTBR")

Introduction
==========

Activate is a framework to persist objects in Scala. It is a [STM](http://en.wikipedia.org/wiki/Software_transactional_memory "STM") (Software Transactional Memory) durable, with pluggable persistence.
Its core is the [RadonSTM](https://github.com/fwbrasil/radon-stm "RadonSTM"), which provides a powerful mechanism for controlling transactions in memory, analogous to the transactions of databases, to do optimistic concurrency control.
The durability of transactions (persistence) is pluggable, and can use persistence in different paradigms such as relational (JDBC), prevalence (Prevayler) and non-relational (MongoDB).

Benefits
==========

The main benefits of the framework are:

* Atomic, consistent, isolated and durable transactions. You can use entities without worrying about concurrency issues.
* Entities are always consistent in memory and in the persistence layer. For example, if rollback occurs, entities in memory stays consistent.
* Transaction propagation control, including nested transactions.
* Transparent Persistence. Just use the entities in transactions and they are automatically persisted.
* Entities are lazy loaded and initialized automatically when needed.	
* Queries are type-safe and consistent, even with objects created in the current transaction. Therefore, an entity created in the same transaction may be returned in a query.

Setup
===========

Download xsbt [sbt-launch.jar](http://typesafe.artifactoryonline.com/typesafe/ivy-releases/org.scala-tools.sbt/sbt-launch/0.11.2/sbt-launch.jar "sbt-launch.jar") and activate [example project](https://github.com/fwbrasil/activate-example/zipball/v0.9 "example project").

Create a sbt launch script like this and put in system path:

	java -XX:MaxPermSize=512m -Xmx512M -noverify -jar /location/of/sbt-launch.jar "$@"

Remember to replace "/location/of/sbt-launch.jar" with the actual location of sbt-launch.jar.
You can also call this command line directly. Remember always to add "-noverify".

Modify project/ActivateExampleBuild.scala and com/example/foo/ActivateExampleContext.scala to determine the storage. Memory storage is the default value.

Call sbt inside activate-example folder and create the eclipse project:

	$ sbt
	> eclipse

Now you can import into eclipse. It is necessary that the scala plugin is installed [http://scala-ide.org/] (http://scala-ide.org/ "http://scala-ide.org/").

If you want to change the storage, simply change the two classes mentioned above, open the console and rebuild the project sbt eclipse with the same command ("eclipse").

Usage
==========

Initially, must be created the context of Activate. The context must be a singleton, so it makes sense to declare as "object":

Prevayler

	import net.fwbrasil.activate.ActivateContext
	import net.fwbrasil.activate.storage.prevayler.PrevaylerMemoryStorage

	object prevaylerContext extends ActivateContext {
		def contextName = "prevaylerContext"
		val storage = new PrevaylerMemoryStorage
	}

Transient memory

	import net.fwbrasil.activate.ActivateContext
	import net.fwbrasil.activate.storage.memory.MemoryStorage

	object memoryContext extends ActivateContext {
		def contextName = "memoryContext"
		val storage = new MemoryStorage
	}

Oracle

	import net.fwbrasil.activate.ActivateContext
	import net.fwbrasil.activate.storage.relational.JdbcRelationalStorage
	import net.fwbrasil.activate.storage.relational.oracleDialect

	object oracleContext extends ActivateContext {
		def contextName = "oracleContext"
		val storage = new SimpleJdbcRelationalStorage {
			val jdbcDriver = "oracle.jdbc.driver.OracleDriver"
			val user = "USER"
			val password = "PASS"
			val url = "jdbc:oracle:thin:@localhost:1521:oracle"
			val dialect = oracleDialect
		}
	}

Mysql

	import net.fwbrasil.activate.ActivateContext
	import net.fwbrasil.activate.storage.relational.JdbcRelationalStorage
	import net.fwbrasil.activate.storage.relational.mySqlDialect

	object mysqlContext extends ActivateContext {
		def contextName = "mysqlContext"
		val storage = new SimpleJdbcRelationalStorage {
			val jdbcDriver = "com.mysql.jdbc.Driver"
			val user = "root"
			val password = "root"
			val url = "jdbc:mysql://127.0.0.1/test"
			val dialect = mySqlDialect
		}
	}

MongoDB

	import net.fwbrasil.activate.ActivateContext
	import net.fwbrasil.activate.storage.mongo.MongoStorage

	object mongoContext extends ActivateContext {
		def contextName = "mongoContext"
		val storage = new MongoStorage {
			val host = "localhost"
			override val port = 27017
			val db = "dbName"
			override val authentication = Option("user", "pass")
		}
	}

It is important that the context name is unique, but you can have multiple contexts in the same VM.

To use the context, import it:

	import prevaylerContext._

Thus, the required classes like Entity and Query and will be in scope. An entity shall extend the trait "Entity":

	abstract class Person(var name: String) extends Entity
	class NaturalPerson(name: String, var motherName: String) extends Person(name)
	class LegalPerson(name: String, var director: NaturalPerson) extends Person(name)

You can declare the properties as val or var, where they are immutable or not.

Use whenever entities within transactions:

	transactional {
		val person = new NaturalPerson("John", "Marie")
		person.name = "John2"
		println(person.name)
	}

It is not necessary to call a method like "store" or "save" to add the entity. Just create, use, and it will be persisted.

Queries:

Perform queries within transactions:

	transactional {
		val result = 
			query {
				(person: Person) => where(person.name :== "Test") select(person)
			}
		for (person <- result)
			println(person.name)
	}
	
The query operators available are :==, :<, :>, :<=, :>=, isNull, isNotNull, :||, :&&, like and regexp. Note that the queries can be made about entities super classes (including abstract trait and class).

There are alternative forms of query. With the allWhere you can use a list of criterias.

	transactional {
		val personList1 = all[Person]
		val personList2 = allWhere[NaturalPerson](_.name :== "Test", _.motherName :== "Mother")
	}

Queries using more than one entity or with nested properties:

	val q2 = query {
		(company: LegalPerson, director: NaturalPerson) => where(company.director :== director) select (company, director)
	}
	val q3 = query {
		(company: LegalPerson) => where(company.director.name :== "Doe") select(company)
	}

Note: Queries involving more than one entity are not supported by MongoStorage.

To delete an entity:

	transactional {
		for(person <- all[Person])
			person.delete
	}

Typically transactional blocks are controlled by the framework. But you can control the transaction as follows:

	val transaction = new Transaction
	transactional(transaction) {
		new NaturalPerson("Test", "Mother")
	}
	transaction.commit

Defining the propagation of the transaction:

	transactional {
		val person = new NaturalPerson("Test", "Mother")
		transactional(mandatory) {
			person.name = "Test2"
		}
		println(person.name)
	}

Nested transactions are a type of propagation:

	transactional {
		val person = new NaturalPerson("Test", "Mother")
		transactional(nested) {
			person.name = "Test2"
		}
		println(person.name)
	}

The propagation available are based on EJB:

* Required
* RequiresNew
* Mandatory
* NotSupported
* Supports
* Never
* Nested

Database
==============

This is the mapping between the types of attributes of entities and types of databases:

Tipo         | Mysql       | Oracle
-------------|-------------|-----------------
ID           | VARCHAR(50) | VARCHAR2(50)
Int          | INTEGER     | INTEGER
Boolean      | BOOLEAN     | NUMBER(1)
Char         | CHAR        | CHAR
String       | VARCHAR     | VARCHAR2
Float        | DOUBLE      | FLOAT
Double       | DOUBLE      | DOUBLE PRECISION
BigDecimal   | DECIMAL     | NUMBER
Date         | LONG        | TIMESTAMP
Calendar     | LONG        | TIMESTAMP
Array[Byte]  | BLOB        | BLOB
Entity       | VARCHAR(50) | VARCHAR2(50)
Enumeration  | VARCHAR(20) | VARCHAR2(20)

* Always add a column "ID" to entities.
* The table name is the name of the entity class.
* The type AbstractInstant (JodaTime) follows the same mapping of Date.

License
=======

The code is licensed under LGPL.
