package com.strumenta.kolasu.emf

import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.ReferenceByName
import com.strumenta.kolasu.model.processProperties
import java.io.ByteArrayOutputStream
import java.io.File
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EEnum
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EPackage
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl
import org.emfjson.jackson.resource.JsonResourceFactory
import java.lang.RuntimeException

fun EPackage.getEClass(javaClass: Class<*>): EClass {
    return this.getEClass(javaClass.simpleName)
}

fun EPackage.getEClass(name: String): EClass {
    return (this.eClassifiers.find { it.name == name } ?: throw IllegalArgumentException("Class not found: $javaClass")) as EClass
}

fun EPackage.getEEnum(javaClass: Class<*>): EEnum {
    return (this.eClassifiers.find { it.name == javaClass.simpleName } ?: throw IllegalArgumentException("Class not found: $javaClass")) as EEnum
}

fun Any.dataToEObject(ePackage: EPackage): EObject {
    val ec = ePackage.getEClass(this.javaClass)
    val eo = ePackage.eFactoryInstance.create(ec)
    return eo
}

fun Node.toEObject(ePackage: EPackage): EObject {
    try {
        val ec = ePackage.getEClass(this.javaClass)
        val eo = ePackage.eFactoryInstance.create(ec)
        this.processProperties { pd ->
            val esf = ec.eAllStructuralFeatures.find { it.name == pd.name }!!
            if (pd.provideNodes) {
                if (pd.multiple) {
                    val elist = eo.eGet(esf) as MutableList<EObject>
                    (pd.value as List<*>).forEach {
                        try {
                            val childEO = (it as Node).toEObject(ePackage)
                            elist.add(childEO)
                        } catch (e: Exception) {
                            throw RuntimeException("Unable to map to EObject child $it in property $pd of $this", e)
                        }
                    }
                } else {
                    if (pd.value == null) {
                        eo.eSet(esf, null)
                    } else {
                        eo.eSet(esf, (pd.value as Node).toEObject(ePackage))
                    }
                }
            } else {
                if (pd.multiple) {
                    TODO()
                } else {
                    if (pd.value is Enum<*>) {
                        val ee = ePackage.getEEnum(pd.value.javaClass)
                        val eev = ee.getEEnumLiteral(pd.value.name)
                        eo.eSet(esf, eev)
                    } else {
                        // this could be not a primitive value but a value that we mapped to an EClass
                        if (pd.value != null) {
                            val eClass = ePackage.eClassifiers.filterIsInstance<EClass>().find { it.name == pd.value.javaClass.simpleName }
                            if (eClass != null) {
                                val eoValue = pd.value.dataToEObject(ePackage)
                                try {
                                    eo.eSet(esf, eoValue)
                                } catch (t: Throwable) {
                                    throw RuntimeException("Issue setting $esf in $eo to $eoValue", t)
                                }
                            } else if (pd.value is ReferenceByName<*>) {
                                val refEC = KOLASU_METAMODEL.getEClass("ReferenceByName")
                                val refEO = KOLASU_METAMODEL.eFactoryInstance.create(refEC)
                                // TODO complete
                                eo.eSet(esf, refEO)
                            } else {
                                try{
                                    eo.eSet(esf, pd.value)
                                } catch (e: Exception) {
                                    throw RuntimeException("Unable to set property $pd of $this. Structural feature: $esf", e)
                                }
                            }
                        } else {
                            try{
                                eo.eSet(esf, pd.value)
                            } catch (e: Exception) {
                                throw RuntimeException("Unable to set property $pd of $this. Structural feature: $esf", e)
                            }
                        }
                    }
                }
            }
        }
        return eo
    } catch (e: Exception) {
        throw RuntimeException("Unable to map to EObject $this", e)
    }
}

fun EObject.saveXMI(xmiFile: File) {
    val resourceSet = ResourceSetImpl()
    resourceSet.resourceFactoryRegistry.extensionToFactoryMap["xmi"] = XMIResourceFactoryImpl()
    val uri: URI = URI.createFileURI(xmiFile.absolutePath)
    val resource: Resource = resourceSet.createResource(uri)
    resource.contents.add(this)
    resource.save(null)
}

fun EPackage.saveAsJson(jsonFile: File, restoringURI:Boolean=true) {
    val startURI = this.eResource().uri
    (this as EObject).saveAsJson(jsonFile)
    if (restoringURI) {
        this.setResourceURI(startURI.toString())
    }
}


fun EObject.saveAsJson(jsonFile: File) {
    val resourceSet = ResourceSetImpl()
    resourceSet.resourceFactoryRegistry.extensionToFactoryMap["json"] = JsonResourceFactory()
    val uri: URI = URI.createFileURI(jsonFile.absolutePath)
    val resource: Resource = resourceSet.createResource(uri)
    resource.contents.add(this)
    resource.save(null)
}

fun EObject.saveAsJson(): String {
    val uri: URI = URI.createURI("dummy-URI")
    val resource: Resource = JsonResourceFactory().createResource(uri)
    resource.contents.add(this)
    val uf = resource.getURIFragment(this)
    println(uf)
    val output = ByteArrayOutputStream()
    resource.save(output, null)
    return output.toString(Charsets.UTF_8.name())
}
