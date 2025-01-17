package org.ithinktree.becky.jprimewrappers;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.ithinktree.becky.CophylogenyLikelihood;

import se.cbb.jprime.apps.dltrs.DLTRSModel;
import se.cbb.jprime.apps.dltrs.EpochDLTProbs;
import se.cbb.jprime.apps.dltrs.ReconciliationHelper;
import se.cbb.jprime.mcmc.ChangeInfo;
import se.cbb.jprime.mcmc.Dependent;
import se.cbb.jprime.topology.GuestHostMap;
import se.cbb.jprime.topology.LeafLeafMap;
import se.cbb.jprime.topology.NamesMap;
import se.cbb.jprime.topology.RBTree;
import se.cbb.jprime.topology.RBTreeEpochDiscretiser;
import dr.evolution.tree.Tree;
import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import dr.inference.model.Variable.ChangeType;
import dr.math.UnivariateFunction;
import dr.math.distributions.Distribution;

@SuppressWarnings("serial")
public class CophylogenyLikelihoodWrapperForJPrIMEDLTRSModel extends
		CophylogenyLikelihood {

	private final DLTRSModel dltrsModel;
	private final ReconciliationHelper reconciliationHelper;
	private final RBTreeEpochDiscretiser rbTreeEpochDiscretiser;
	private final EpochDLTProbs dltProbs;

	public CophylogenyLikelihoodWrapperForJPrIMEDLTRSModel(final String name, final Tree host, final Tree guest,
			final Parameter duplicationRate, final Parameter lossRate, final Parameter transferRate, final boolean normalize, final Taxa guestTaxa, final String hostAttributeName) {
		
		super(name);
		
		if (host instanceof Model) addModel((Model) host);
		if (guest instanceof Model) addModel((Model) guest);
		addVariable(duplicationRate);
		addVariable(lossRate);
		addVariable(transferRate);
		
		final RBTree jprimeHostRBTree = new JPrIMERBTreeWrapperForBEASTTree(host);
		final NamesMap jprimeHostNamesMap = new JPrIMENamesMapWrapperForBEASTTree(host);
		final RBTree jprimeGuestRBTree = new JPrIMERBTreeWrapperForBEASTTree(guest);
		final NamesMap jprimeGuestNamesMap = new JPrIMENamesMapWrapperForBEASTTree(guest);
		rbTreeEpochDiscretiser = new RBTreeEpochDiscretiser(jprimeHostRBTree, jprimeHostNamesMap, new JPrIMETimesMapWrapperForBEASTTree(host));
		
		final GuestHostMap guestHostMap = new GuestHostMap();
		for (Iterator<Taxon> taxa = guestTaxa.iterator(); taxa.hasNext(); ) {
			Taxon t = taxa.next();
			guestHostMap.add(t.getId(), ((Taxon) t.getAttribute(hostAttributeName)).getId());
		}
		
		reconciliationHelper = new ReconciliationHelper(jprimeGuestRBTree, jprimeHostRBTree, rbTreeEpochDiscretiser, new LeafLeafMap(guestHostMap, jprimeGuestRBTree, jprimeGuestNamesMap, jprimeHostRBTree, jprimeHostNamesMap));
		
		dltProbs = new EpochDLTProbs(rbTreeEpochDiscretiser,
				new JPrIMEDoubleParameterWrapperForBEASTParameter(duplicationRate),
				new JPrIMEDoubleParameterWrapperForBEASTParameter(lossRate),
				new JPrIMEDoubleParameterWrapperForBEASTParameter(transferRate),
				normalize
				);
		
		final Distribution distributionModel = new Distribution(){
            public double cdf(double arg0) {
                return 1.0;
            }
            public UnivariateFunction getProbabilityDensityFunction() {
                throw new UnsupportedOperationException();
            }
            public double logPdf(double arg0) {
                return 0.0;
            }
            public double mean() {
                return 1;
            }
            public double pdf(double arg0) {
                return 1.0;
            }
            public double quantile(double arg0) {
                throw new UnsupportedOperationException();
            }
            public double variance() {
                throw new UnsupportedOperationException();
            }};
            		
		dltrsModel = new DLTRSModel(jprimeGuestRBTree, jprimeHostRBTree, reconciliationHelper, new JPrIMEDoubleMapWrapperForBEASTTree(guest), dltProbs, new JPrIMEContinuous1DPDDependentWrapperForBEASTDistribution(distributionModel));
	}
	
	private final Map<Dependent,ChangeInfo> changeInfos = new HashMap<Dependent,ChangeInfo>();
	
	public double getLogLikelihood() {
		makeDirty(); // Effectively bypasses its own purpose, but unavoidable (crazy bug)
		return super.getLogLikelihood();
	}
	
	protected double calculateLogLikelihood() {
		
		if (modelDirty) {
			rbTreeEpochDiscretiser.cacheAndUpdate(changeInfos, true);
			reconciliationHelper.cacheAndUpdate(changeInfos, true);
		}
		dltProbs.cacheAndUpdate(changeInfos, true);
		dltrsModel.cacheAndUpdate(changeInfos, true);
		changeInfos.clear();
		return dltrsModel.getDataProbability().getLogValue();
	}


	private boolean modelDirty = true;
	@Override
	protected void handleModelChangedEvent(Model model, Object object, int index) {
		modelDirty = true;
		super.handleModelChangedEvent(model, object, index);
	}

	protected void handleVariableChangedEvent(@SuppressWarnings("rawtypes") Variable variable, int index, ChangeType type) {
		makeDirty();
	}
}
