package com.strumenta.kolasu.emf

import com.strumenta.kolasu.model.*
import org.eclipse.emf.ecore.*
import java.io.File
import java.util.*
import kotlin.collections.HashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.*


interface EDataTypeHandler {
    fun canHandle(ktype: KType) : Boolean
    fun toDataType(ktype: KType): EDataType
}

object BasicKClassDataTypeHandler : EDataTypeHandler {
    override fun canHandle(ktype: KType): Boolean {
        return ktype.classifier is KClass<*>
    }

    override fun toDataType(ktype: KType): EDataType {
        val kclass = ktype.classifier as KClass<*>
        val eDataType = EcoreFactory.eINSTANCE.createEDataType()
        eDataType.name = kclass.simpleName!!
        eDataType.instanceClass = kclass.java
        return eDataType
    }

}

interface EClassTypeHandler {
    fun canHandle(ktype: KType) : Boolean {
        return if (ktype.classifier is KClass<*>) {
            canHandle(ktype.classifier as KClass<*>)
        } else {
            false
        }
    }
    fun canHandle(kclass: KClass<*>) : Boolean
    fun toEClass(kclass: KClass<*>, eClassProvider: ClassifiersProvider): EClass
}

interface ClassifiersProvider {
    fun isDataType(ktype: KType) : Boolean {
        try {
            provideDataType(ktype)
            return true
        } catch (e: Exception) {
            return false
        }
    }
    fun provideClass(kClass: KClass<*>): EClass
    fun provideDataType(ktype: KType): EDataType
}

object StandardEClassHandler : EClassTypeHandler {
        override fun canHandle(kclass: KClass<*>): Boolean {
            if (kclass == Named::class) {
                return true
            } else if (kclass == String::class) {
                return false
            } else if (kclass == Boolean::class) {
                return false
            } else if (kclass == Int::class) {
                return false
            } else if (kclass == ReferenceByName::class) {
                return false
            } else {
                //TODO("Not yet implemented")
                return true
            }
        }

        override fun toEClass(kclass: KClass<*>, classifiersProvider: ClassifiersProvider): EClass {
            if (kclass == Named::class) {
                return KOLASU_METAMODEL.getEClass(Named::class.java)
            } else {
                val ec = EcoreFactory.eINSTANCE.createEClass()
                ec.isAbstract = kclass.isSealed || kclass.isAbstract
                ec.isInterface = kclass.java.isInterface
                ec.name = kclass.simpleName
                kclass.supertypes.forEach {
                    if (it == Any::class.createType()) {
                        // ignore
                    } else {
                        val parent = classifiersProvider.provideClass(it.classifier as KClass<*>)
                        ec.eSuperTypes.add(parent)
                    }
                }
                kclass.memberProperties.forEach {
                    val isDerived = it.annotations.any { it is Derived }

                    if (!isDerived) {
                        val isMany = it.returnType.isSubtypeOf(Collection::class.createType(listOf(KTypeProjection.STAR)))
                        val baseType = if (isMany) it.returnType.arguments[0].type!! else it.returnType
                        if (baseType.classifier == ReferenceByName::class){
                            TODO()
                        }
                        val isAttr = classifiersProvider.isDataType(baseType)
                        if (isAttr) {
                            val ea = EcoreFactory.eINSTANCE.createEAttribute()
                            ea.name = it.name
                            if (isMany) {
                                ea.upperBound = -1
                                ea.lowerBound = 0
                            }
                            ea.eType = classifiersProvider.provideDataType(baseType)
                            ec.eStructuralFeatures.add(ea)
                        } else {
                            val er = EcoreFactory.eINSTANCE.createEReference()
                            er.name = it.name
                            if (isMany) {
                                er.upperBound = -1
                                er.lowerBound = 0
                            }
                            er.isContainment = true
                            er.eType = classifiersProvider.provideClass(baseType.classifier as KClass<*>)
                            ec.eStructuralFeatures.add(er)
                        }
                    }
                }
                return ec
                //TODO("Not yet implemented")
            }
        }

    }

class MetamodelBuilder(packageName: String, nsURI: String, nsPrefix: String) : ClassifiersProvider {

    private val ePackage: EPackage
    private val eClasses = HashMap<KClass<*>, EClass>()
    private val dataTypes = HashMap<KType, EDataType>()
    private val eclassTypeHandlers = LinkedList<EClassTypeHandler>()
    private val dataTypeHandlers = LinkedList<EDataTypeHandler>()

    init {
        ePackage = EcoreFactory.eINSTANCE.createEPackage()
        ePackage.name = packageName
        ePackage.nsURI = nsURI
        ePackage.nsPrefix = nsPrefix
        ePackage.setResourceURI(nsURI)

        eclassTypeHandlers.add(StandardEClassHandler)
    }

    fun addDataTypeHandler(eDataTypeHandler: EDataTypeHandler) {
        dataTypeHandlers.add(eDataTypeHandler)
    }

    fun addEClassTypeHandler(eClassTypeHandler: EClassTypeHandler) {
        eclassTypeHandlers.add(eClassTypeHandler)
    }

    private fun createEEnum(kClass: KClass<out Enum<*>>): EEnum {
        val eEnum = EcoreFactory.eINSTANCE.createEEnum()
        eEnum.name = kClass.simpleName
        kClass.java.enumConstants.forEach {
            var eLiteral = EcoreFactory.eINSTANCE.createEEnumLiteral()
            eLiteral.name = it.name
            eLiteral.value = it.ordinal
            eEnum.eLiterals.add(eLiteral)
        }
        return eEnum
    }

    override fun provideDataType(ktype: KType): EDataType {
        if (!dataTypes.containsKey(ktype)) {
            var eDataType = EcoreFactory.eINSTANCE.createEDataType()
            when {
                ktype.classifier == String::class -> {
                    eDataType.name = "String"
                    eDataType.instanceClass = String::class.java
                }
                ktype.classifier == Boolean::class -> {
                    eDataType.name = "Boolean"
                    eDataType.instanceClass = Boolean::class.java
                }
                ktype.classifier == Int::class -> {
                    eDataType.name = "Int"
                    eDataType.instanceClass = Int::class.java
                }
                (ktype.classifier as? KClass<*>)?.isSubclassOf(Enum::class) == true -> {
                    eDataType = createEEnum(ktype.classifier as KClass<out Enum<*>>)
                }
                else -> {
                    val handler = dataTypeHandlers.find { it.canHandle(ktype) }
                    if (handler == null) {
                        throw RuntimeException("Unable to handle data type $ktype, with classifier ${ktype.classifier}")
                    } else {
                        eDataType = handler.toDataType(ktype)
                    }
                }
            }
            ePackage.eClassifiers.add(eDataType)
            dataTypes[ktype] = eDataType
        }
        return dataTypes[ktype]!!
    }

    private fun nodeClassToEClass(kClass: KClass<*>): EClass {
        val eClass = EcoreFactory.eINSTANCE.createEClass()
        kClass.superclasses.forEach {
            if (it != Any::class) {
                if (it == Node::class) {
                    eClass.eSuperTypes.add(KOLASU_METAMODEL.getEClass("ASTNode"))
                } else {
                    eClass.eSuperTypes.add(provideClass(it))
                }
            }
        }
        eClass.name = kClass.simpleName
        eClass.isAbstract = kClass.isAbstract || kClass.isSealed
        kClass.java.processProperties { prop ->
            try {
                if (eClass.eAllStructuralFeatures.any { sf -> sf.name == prop.name }) {
                    // skip
                } else {
                    // do not process inherited properties
                    if (prop.provideNodes) {
                        val ec = EcoreFactory.eINSTANCE.createEReference()
                        ec.name = prop.name
                        if (prop.multiple) {
                            ec.lowerBound = 0
                            ec.upperBound = -1
                        } else {
                            ec.lowerBound = 0
                            ec.upperBound = 1
                        }
                        ec.isContainment = true
                        ec.eType = provideClass(prop.valueType.classifier as KClass<*>)
                        eClass.eStructuralFeatures.add(ec)
                    } else if (prop.valueType.classifier == ReferenceByName::class) {
                        val ec = EcoreFactory.eINSTANCE.createEReference()
                        ec.name = prop.name
                        ec.isContainment = true
                        ec.eGenericType = EcoreFactory.eINSTANCE.createEGenericType().apply {
                            this.eClassifier = KOLASU_METAMODEL.getEClass(ReferenceByName::class.java)
                            val eClassForReferenced : EClass = provideClass(prop.valueType.arguments[0].type!!.classifier!! as KClass<*>)
                            this.eTypeArguments.add(EcoreFactory.eINSTANCE.createEGenericType().apply {
                                this.eClassifier = eClassForReferenced
                            })
                        }
                        eClass.eStructuralFeatures.add(ec)
                    } else {
                        val ch = eclassTypeHandlers.find { it.canHandle(prop.valueType) }
                        if (ch != null) {
                            // We can treat it like a class
                            val eContainment = EcoreFactory.eINSTANCE.createEReference()
                            eContainment.name = prop.name
                            if (prop.multiple) {
                                eContainment.lowerBound = 0
                                eContainment.upperBound = -1
                            } else {
                                eContainment.lowerBound = 0
                                eContainment.upperBound = 1
                            }
                            eContainment.isContainment = true
                            eContainment.eType = provideClass(prop.valueType.classifier as KClass<*>)
                            eClass.eStructuralFeatures.add(eContainment)
                        } else {
                            val ea = EcoreFactory.eINSTANCE.createEAttribute()
                            ea.name = prop.name
                            if (prop.multiple) {
                                ea.lowerBound = 0
                                ea.upperBound = -1
                            } else {
                                ea.lowerBound = 0
                                ea.upperBound = 1
                            }
                            ea.eType = provideDataType(prop.valueType)
                            eClass.eStructuralFeatures.add(ea)
                        }
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException("Issue processing property $prop in class $kClass", e)
            }
        }
        return eClass
    }

    override fun provideClass(kClass: KClass<*>): EClass {
        if (kClass == Node::class) {
            return KOLASU_METAMODEL.getEClass("ASTNode")
        }
        if (kClass == Named::class) {
            return KOLASU_METAMODEL.getEClass("Named")
        }
        if (!eClasses.containsKey(kClass)) {
            val eClass = if (kClass.isSubclassOf(Node::class)) {
                nodeClassToEClass(kClass)
            } else {
                val eth = eclassTypeHandlers.find { it.canHandle(kClass) } ?: throw RuntimeException("Unable to handle class $kClass")
                eth.toEClass(kClass, this)
            }
            ePackage.eClassifiers.add(eClass)
            eClasses[kClass] = eClass
            if (kClass.isSealed) {
                kClass.sealedSubclasses.forEach { provideClass(it) }
            }
        }
        return eClasses[kClass]!!
    }

    fun generate(): EPackage {
        return ePackage
    }
}

fun main(args: Array<String>) {
    KOLASU_METAMODEL.saveEcore(File("kolasu.ecore"))
}