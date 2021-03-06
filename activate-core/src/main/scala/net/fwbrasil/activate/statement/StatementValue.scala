package net.fwbrasil.activate.statement

import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.entity.ValueContext
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.statement.StatementMocks._
import net.fwbrasil.activate.util.ManifestUtil._

class StatementValue()

abstract class StatementSelectValue[V]() extends StatementValue {
	def entityValue: EntityValue[_]
}

trait StatementValueContext extends ValueContext {

	private[fwbrasil] def toStatementValueRef[V](ref: Var[V]): StatementSelectValue[V] = {
		val (entity, path) = propertyPath(ref)
		val sourceOption = From.entitySourceFor(entity)
		if (sourceOption != None)
			new StatementEntitySourcePropertyValue[V](sourceOption.get, path: _*)
		else new SimpleValue[V](() => ref.get.get, ref.tval)

	}

	private[activate] def propertyPath(ref: Var[_]) = {
		ref match {
			case ref: FakeVar[_] => {
				var propertyPath = List[Var[_]](ref)
				var entity = ref.outerEntity
				var originVar: FakeVar[_] = ref.originVar
				while (originVar != null) {
					propertyPath ::= originVar
					entity = originVar.outerEntity
					originVar = originVar.originVar
				}
				(entity, propertyPath)
			}
			case ref: Var[_] =>
				(null, Nil)
		}
	}

	private def toStatementValueEntity[E <: Entity](fEntity: () => E): StatementEntityValue[E] = {
		val entity = fEntity()
		val sourceOption = From.entitySourceFor(entity)
		if (sourceOption != None)
			new StatementEntitySourceValue(sourceOption.get)
		else
			new StatementEntityInstanceValue(fEntity)
	}

	implicit def toStatementValueEntityValue[V](value: => V)(implicit m: Option[V] => EntityValue[V]): StatementSelectValue[V] =
		toStatementValueEntityValueOption(
			if (value.isInstanceOf[Option[_]])
				value.asInstanceOf[Option[V]]
			else
				Option(value))

	implicit def toStatementValueEntityValueOption[V](value: => Option[V])(implicit m: Option[V] => EntityValue[V]): StatementSelectValue[V] =
		StatementMocks.lastFakeVarCalled match {
			case Some(ref: Var[_]) =>
				toStatementValueRef(ref)
			case other =>
				value.getOrElse(null.asInstanceOf[V]) match {
					case entity: Entity =>
						toStatementValueEntity(() => value.getOrElse(null.asInstanceOf[V]).asInstanceOf[Entity]).asInstanceOf[StatementSelectValue[V]]
					case other =>
						new SimpleValue[V](() => value.getOrElse(null.asInstanceOf[V]), m)
				}
		}

}

object A {
	abstract class FunnyValue[V](value: V) {
		def print =
			value.toString
	}
	implicit def funnyValue[V](value: => V): FunnyValue[V] = new FunnyValue(value) {}
	def m[F](f: F)(implicit tval: (=> F) => FunnyValue[F]) = {}
	"a".print
}

abstract class StatementEntityValue[V]() extends StatementSelectValue[V]

case class StatementEntityInstanceValue[E <: Entity](val fEntity: () => E) extends StatementEntityValue[E] {
	def entity = fEntity()
	def entityId = entity.id
	override def entityValue = EntityInstanceEntityValue[E](Option(entity))(manifestClass[E](entity.getClass))
	override def toString = entityId
	override def hashCode = System.identityHashCode(this)
}

case class StatementEntitySourceValue[V](val entitySource: EntitySource) extends StatementEntityValue[V] {
	override def entityValue: EntityValue[_] = EntityInstanceEntityValue(None)(manifestClass(entitySource.entityClass))
	override def toString = entitySource.name
}

case class StatementEntitySourcePropertyValue[P](override val entitySource: EntitySource, val propertyPathVars: Var[_]*) extends StatementEntitySourceValue[P](entitySource) {
	def lastVar = propertyPathVars.last
	def propertyPathNames =
		for (prop <- propertyPathVars)
			yield prop.name
	override def entityValue: EntityValue[_] = lastVar.asInstanceOf[StatementMocks.FakeVar[_]].entityValueMock.asInstanceOf[EntityValue[_]]
	override def toString = entitySource.name + "." + propertyPathNames.mkString(".")
}

case class SimpleValue[V](val fAnyValue: () => V, val f: (Option[V]) => EntityValue[V]) extends StatementSelectValue[V] {
	def anyValue = fAnyValue()
	//	require(anyValue != null)
	def entityValue: EntityValue[V] = f(Option(anyValue))
	override def toString =
		if (anyValue == null)
			"null"
		else anyValue.toString
}