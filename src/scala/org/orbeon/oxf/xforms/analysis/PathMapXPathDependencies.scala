/**
 *  Copyright (C) 2010 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis

import controls.LHHATrait
import model.Model
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.saxon.om.NodeInfo
import collection.mutable.{HashSet, HashMap, Set}
import org.orbeon.saxon.dom4j.NodeWrapper
import org.orbeon.oxf.xforms._
import org.w3c.dom.Node._
import org.orbeon.oxf.common.OXFException
import java.util.{Map => JMap}

class PathMapXPathDependencies(var logger: IndentedLogger, staticState: XFormsStaticState)// Constructor for unit tests
        extends XPathDependencies {

    private var containingDocument: XFormsContainingDocument = _

    // Represent the state of a model between refreshes
    private class ModelState {

        var structuralChanges = false
        var calculateClean = false   // start dirty
        var validateClean = false    // start dirty

        def refreshDone() {
            structuralChanges = false

            calculateClean = false
            validateClean = false
        }

        def markValueChanged(node: NodeInfo) {
            val instance = containingDocument.getInstanceForNode(node)
            RefreshState.modifiedPaths.put(instance.getPrefixedId, PathMapXPathDependencies.createFingerprintPath(instance, node))
        }

        def markStructuralChange() {
            structuralChanges = true

            calculateClean = false
            validateClean = false
        }

        def rebuildDone() {
            calculateClean = false
            validateClean = false
        }

        def mipDirty(mip: Model#Bind#MIP): Boolean = mip.isValidateMIP && !validateClean || !calculateClean
    }

    // Set of models
    private val modelStates = new HashMap[String, ModelState]

    private def getModelState(modelPrefixedId: String): ModelState = {
        modelStates.get(modelPrefixedId) match {
            case Some(modelState) => modelState
            case None =>
                val modelState = new ModelState
                modelStates.put(modelPrefixedId, modelState)
                modelState
        }
    }

    // Used between refresh start and refresh done
    private var inRefresh = false

    private object RefreshState {
        var structuralChangesModelsCached = false
        val structuralChangeModelsCache = new HashSet[String]

        // Caches to speedup checks on repeated items
        val modifiedBindingCache = new HashMap[String, UpdateResult]
        val modifiedValueCache = new HashMap[String, UpdateResult]
        val modifiedLHHACache = new HashMap[String, Boolean]

        // Statistics
        var bindingUpdateCount: Int = 0
        var valueUpdateCount: Int = 0


        var bindingXPathOptimizedCount: Int = 0
        var valueXPathOptimizedCount: Int = 0

        // Modified paths by instance
        val modifiedPaths = new MapSet[String, String]

        def getStructuralChangeModels: Set[String] = {
            if (!structuralChangesModelsCached) {
                // Add all model ids that have structuralChanges set to true
                structuralChangeModelsCache ++= modelStates filter (_._2.structuralChanges) map (_._1)
                structuralChangesModelsCached = true;
            }
            structuralChangeModelsCache
        }

        def clear() {
            structuralChangesModelsCached = false
            modifiedPaths.clear()
            structuralChangeModelsCache.clear()

            modifiedBindingCache.clear()
            modifiedValueCache.clear()

            modifiedLHHACache.clear()

            bindingUpdateCount = 0
            valueUpdateCount = 0

            bindingXPathOptimizedCount = 0
            valueXPathOptimizedCount = 0
        }
    }

    private val modifiedMIPCache = new HashMap[String, UpdateResult]
    private var mipUpdateCount: Int = 0
    private var mipXPathOptimizedCount: Int = 0

    private var lhhaEvaluationCount: Int = 0
    private var lhhaOptimizedCount: Int = 0
    private var lhhaUnknownDependencies: Int = 0
    private var lhhaMissCount: Int = 0
    private var lhhaHitCount: Int = 0

    def this(containingDocument: XFormsContainingDocument) {
        // Defer logger initialization as controls might not have been initialized at time this constructor is called.
        this(null, containingDocument.getStaticState)
        this.containingDocument = containingDocument
    }

    def getLogger: IndentedLogger = {
        if (logger eq null) // none was passed
            logger = containingDocument.getControls.getIndentedLogger
        return logger
    }

    def markValueChanged(model: XFormsModel, nodeInfo: NodeInfo) {

        // Caller must only call this for a mutable node belonging to the given model
        require(nodeInfo.isInstanceOf[NodeWrapper])
        require(model.getInstanceForNode(nodeInfo).getModel(containingDocument) == model)

        if (!getModelState(model.getPrefixedId).structuralChanges) {
            // Only care about path changes if there is no structural change for this model, since structural changes
            // for now disable any more subtle path-based check.
            getModelState(model.getPrefixedId).markValueChanged(nodeInfo)
        }
    }

    def markStructuralChange(model: XFormsModel, instance: XFormsInstance) {
        getModelState(model.getPrefixedId).markStructuralChange()
    }

    def rebuildDone(model: Model) {
        getModelState(model.prefixedId).rebuildDone()
    }

    def recalculateDone(model: Model) {
        // Say that for this model, calculate binds are clean and can be checked for modifications based on value changes
        getModelState(model.prefixedId).calculateClean = true
    }

    def revalidateDone(model: Model) {
        // Say that for this model, validate binds are clean and can be checked for modifications based on value changes
        getModelState(model.prefixedId).validateClean = true
    }

    def refreshStart() {
        inRefresh = true
//        if (touchedMIPNodes.size() > 0) {
//            // All revalidations and recalculations are done, process information about nodes touched
//            for (final NodeInfo nodeInfo : touchedMIPNodes) {
//                if (InstanceData.getPreviousInheritedRelevant(nodeInfo) != InstanceData.getInheritedRelevant(nodeInfo)
//                        || InstanceData.getPreviousInheritedReadonly(nodeInfo) != InstanceData.getInheritedReadonly(nodeInfo)
//                        || InstanceData.getPreviousRequired(nodeInfo) != InstanceData.getRequired(nodeInfo)
//                        || InstanceData.getPreviousValid(nodeInfo) != InstanceData.getValid(nodeInfo)) {
//                    markMIPChanged(model, nodeInfo)
//                }
//            }
//        }
    }

    def refreshDone() {

        if (getLogger.isDebugEnabled)
            getLogger.logDebug("dependencies", "refresh done",
                Array("bindings updated", RefreshState.bindingUpdateCount.toString,
                      "values updated", RefreshState.valueUpdateCount.toString,
                      "MIPs updated", mipUpdateCount.toString,
                      "Binding XPath optimized", RefreshState.bindingXPathOptimizedCount.toString,
                      "Value XPath optimized", RefreshState.valueXPathOptimizedCount.toString,
                      "MIP XPath optimized", mipXPathOptimizedCount.toString,
                      "Total XPath optimized", (RefreshState.bindingXPathOptimizedCount + RefreshState.valueXPathOptimizedCount + mipXPathOptimizedCount).toString): _*)

        for (modelState <- modelStates.values)
            modelState.refreshDone()

        RefreshState.clear()

        modifiedMIPCache.clear()
        mipUpdateCount = 0
        mipXPathOptimizedCount = 0

        inRefresh = false
    }

    def afterInitialResponse {
        outputLHHAStats()
    }

    def beforeUpdateResponse {
        lhhaEvaluationCount = 0
        lhhaOptimizedCount = 0
        lhhaUnknownDependencies = 0
        lhhaMissCount = 0
        lhhaHitCount = 0
    }

    def afterUpdateResponse {
        outputLHHAStats()
    }

    def notifyComputeLHHA: Unit = lhhaEvaluationCount += 1
    def notifyOptimizeLHHA: Unit = lhhaOptimizedCount += 1

    private def outputLHHAStats() {
        if (getLogger.isDebugEnabled)
            getLogger.logDebug("dependencies", "summary after response",
                Array("lhha evaluations", lhhaEvaluationCount.toString,
                      "lhha optimized", lhhaOptimizedCount.toString,
                      "lhha unknown dependencies", lhhaUnknownDependencies.toString,
                      "lhha intersections", lhhaHitCount.toString,
                      "lhha disjoints", lhhaMissCount.toString): _*)
    }

    // For unit tests
    def markStructuralChangeTest(modelPrefixedId: String) {
        getModelState(modelPrefixedId).markStructuralChange()
    }

    // For unit tests
    def setModifiedPathTest(instance: String, namespaces: JMap[String, String], path: String) {
        assert(RefreshState.modifiedPaths.isEmpty)

        RefreshState.modifiedPaths.put(instance, PathMapXPathAnalysis.getInternalPath(namespaces, path))
    }

    private class UpdateResult(val requireUpdate: Boolean, val savedEvaluations: Int)

    def requireBindingUpdate(controlPrefixedId: String): Boolean = {

        assert(inRefresh)

        val cached = RefreshState.modifiedBindingCache.get(controlPrefixedId)
        val updateResult: UpdateResult =
            cached match {
                case Some(result) => result
                case None => {
                    val control = staticState.getControlAnalysis(controlPrefixedId)
                    val tempResult = control.getBindingAnalysis match {
                        case None =>
                            // Control does not have an XPath binding
                            new UpdateResult(false, 0)
                        case Some(analysis) if !analysis.figuredOutDependencies =>
                            // Binding dependencies are unknown
                            new UpdateResult(true, 0)// savedEvaluations is N/A
                        case Some(analysis) =>
                            // Binding dependencies are known
                            new UpdateResult(
                                analysis.intersectsModels(RefreshState.getStructuralChangeModels) || analysis.intersectsBinding(RefreshState.modifiedPaths)
                                , control.bindingXPathEvaluations)
                    }

                    if (tempResult.requireUpdate && getLogger.isDebugEnabled)
                        getLogger.logDebug("dependencies", "binding requires update",
                                Array("prefixed id", controlPrefixedId, "XPath", control.getBindingAnalysis.get.xpathString): _*)

                    if (control.isWithinRepeat)
                        RefreshState.modifiedBindingCache.put(controlPrefixedId, tempResult)
                    tempResult
                }
            }

        if (updateResult.requireUpdate) {
            RefreshState.bindingUpdateCount += 1
        } else {
            // Update not required
            RefreshState.bindingXPathOptimizedCount += updateResult.savedEvaluations
        }
        updateResult.requireUpdate
    }

    def requireValueUpdate(controlPrefixedId: String): Boolean = {

        assert(inRefresh)

        val cached = RefreshState.modifiedValueCache.get(controlPrefixedId)
        val (updateResult, valueAnalysis) =
            cached match {
                case Some(result) => (result, if (result.requireUpdate) staticState.getControlAnalysis(controlPrefixedId).getValueAnalysis else null)
                case None => {
                    val control = staticState.getControlAnalysis(controlPrefixedId)
                    val tempValueAnalysis = control.getValueAnalysis
                    val tempUpdateResult = tempValueAnalysis match {
                        case None =>
                            // Control does not have a value
                            new UpdateResult(true, 0)//TODO: should be able to return false here; change once markDirty is handled better
                        case Some(analysis) if !analysis.figuredOutDependencies =>
                            // Value dependencies are unknown
                            new UpdateResult(true, 0)// savedEvaluations is N/A
                        case Some(analysis) =>
                            // Value dependencies are known
                            new UpdateResult(analysis.intersectsModels(RefreshState.getStructuralChangeModels) || analysis.intersectsValue(RefreshState.modifiedPaths)
                                , if (control.value.isDefined) 1 else 0)
                    }
                    if (tempUpdateResult.requireUpdate && tempValueAnalysis.isDefined && getLogger.isDebugEnabled)
                        getLogger.logDebug("dependencies", "value requires update",
                                Array("prefixed id", controlPrefixedId, "XPath", tempValueAnalysis.get.xpathString): _*)

                    if (control.isWithinRepeat)
                        RefreshState.modifiedValueCache.put(controlPrefixedId, tempUpdateResult)
                    (tempUpdateResult, tempValueAnalysis)
                }
            }

        if (updateResult.requireUpdate && valueAnalysis.isDefined) {// TODO: see above, check on valueAnalysis only because non-value controls still call this method
            RefreshState.valueUpdateCount += 1
        } else {
            // Update not required
            RefreshState.valueXPathOptimizedCount += updateResult.savedEvaluations
        }
        updateResult.requireUpdate
    }

    def requireLHHAUpdate(lhha: XFormsConstants.LHHA, controlPrefixedId: String): Boolean = {

        assert(inRefresh) // LHHA is evaluated lazily typically outside of refresh, but LHHA invalidation takes place during refresh

        RefreshState.modifiedLHHACache.get(controlPrefixedId) match {
            case Some(result) => result // cached
            case None => // not cached
                staticState.getControlAnalysis(controlPrefixedId) match {
                    case control: LHHATrait => // control found
                        val result = control.getLHHAValueAnalysis(lhha.name.toLowerCase) match {
                            case Some(analysis) if !analysis.figuredOutDependencies => // dependencies are unknown
                                lhhaUnknownDependencies += 1
                                true
                            case Some(analysis) => // dependencies are known
                                val result = analysis.intersectsModels(RefreshState.getStructuralChangeModels) || analysis.intersectsValue(RefreshState.modifiedPaths)
                                if (result) lhhaHitCount += 1 else lhhaMissCount += 1
                                result
                            case None => throw new OXFException("Control " + controlPrefixedId + " doesn't have LHHA " + lhha.name.toLowerCase)
                        }
                        if (control.isWithinRepeat)
                            RefreshState.modifiedLHHACache.put(controlPrefixedId, result)
                        result
                    case _ => throw new OXFException("Control " + controlPrefixedId + " not found")
                }
        }
    }

    def hasAnyCalculationBind(model: Model, instancePrefixedId: String) = {
        !model.figuredAllBindRefAnalysis || model.computedBindExpressionsInstances.contains(instancePrefixedId)
    }

    def hasAnyValidationBind(model: Model, instancePrefixedId: String) = {
        !model.figuredAllBindRefAnalysis || model.validationBindInstances.contains(instancePrefixedId)
    }

//    public void visitInstanceNode(XFormsModel model, NodeInfo nodeInfo) {
//        if (!touchedMIPNodes.contains(nodeInfo)) {
//            // First time this is called for a NodeInfo: keep old MIP values and remember NodeInfo
//            InstanceData.saveMIPs(nodeInfo)
//            touchedMIPNodes.add(nodeInfo)
//        }
//    }

    def hasAnyCalculationBind(model: Model) = model.hasCalculateComputedCustomBind
    def hasAnyValidationBind(model: Model) = model.hasValidateBind

    def requireModelMIPUpdate(model: Model, bindId: String, mipName: String): Boolean = {
        val bind = model.bindsById.get(bindId)
        bind.getMIP(mipName) match {
            case Some(mip) =>
                val cached = modifiedMIPCache.get(bind.prefixedId)
                val updateResult =
                    cached match {
                        case Some(result) => result
                        case None => {
                            val mipAnalysis = mip.analysis
                            val tempUpdateResult =
                                if (!mipAnalysis.figuredOutDependencies) {
                                    // Value dependencies are unknown
                                    new UpdateResult(true, 0)// savedEvaluations is N/A
                                } else if (getModelState(model.prefixedId).mipDirty(mip)) {
                                    // Value dependencies are known but the MIP is dirty because validation or calculation binds are dirty
                                    new UpdateResult(true, 0)// savedEvaluations is N/A
                                } else {
                                    // Value dependencies are known
                                    new UpdateResult(
                                        mipAnalysis.intersectsModels(RefreshState.getStructuralChangeModels) || mipAnalysis.intersectsValue(RefreshState.modifiedPaths)
                                        , 1)
                                }
                            if (tempUpdateResult.requireUpdate && mipAnalysis != null && getLogger.isDebugEnabled) {
                                getLogger.logDebug("dependencies", "MIP requires update",
                                    Array("prefixed id", bind.prefixedId, "MIP name", mip.name, "XPath", mipAnalysis.xpathString): _*)
                            }

                            modifiedMIPCache.put(bind.prefixedId, tempUpdateResult)
                            tempUpdateResult
                        }
                    }

                if (updateResult.requireUpdate) {
                    mipUpdateCount += 1
                } else {
                    // Update not required
                    mipXPathOptimizedCount += updateResult.savedEvaluations
                }
                updateResult.requireUpdate
            case None =>
                false // no such MIP
        }
    }
}

object PathMapXPathDependencies {

    /**
     * Create a fingerprinted path of the form: 3142/1425/@1232 from a node.
     */
    private def createFingerprintPath(instance: XFormsInstance, node: NodeInfo): String = {

        // Create an immutable list with ancestor-or-self nodes up to but not including the document node
        var ancestorOrSelf: List[NodeInfo] = Nil
        var currentNode = node
        while (currentNode != null && currentNode.getNodeKind != DOCUMENT_NODE) {
            ancestorOrSelf = currentNode :: ancestorOrSelf
            currentNode = currentNode.getParent
        }

        // Join instance('...') and a fingerprint representation of the element and attribute nodes
        (if (ancestorOrSelf.size > 1) // first is the root element, which we skip as that corresponds to instance('...')
            ancestorOrSelf.tail map (node => node.getNodeKind match {
                case ELEMENT_NODE => node.getFingerprint
                case ATTRIBUTE_NODE => "@" + node.getFingerprint
            })
        else
            Nil) mkString "/"
    }
}