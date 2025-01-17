/**
 * CoevolutionSimulator.java
 * 
 * BECKY - Bayesian Estimation of Coevolutionary KrYteria
 * 
 */
package org.ithinktree.becky.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.ithinktree.becky.CophylogenyLikelihood;
import org.ithinktree.becky.CophylogenyModel;
import org.ithinktree.becky.CophylogenyModel.Utils;
import org.ithinktree.becky.NodeRefProvider;
import org.ithinktree.becky.SimpleCophylogenyModel;
import org.ithinktree.becky.SimpleCophylogenyModel.EventType;

import dr.app.seqgen.SeqGen;
import dr.app.tools.NexusExporter;
import dr.app.util.Arguments;
import dr.app.util.Arguments.ArgumentException;
import dr.evolution.tree.MutableTree;
import dr.evolution.tree.NodeRef;
import dr.evolution.tree.SimpleNode;
import dr.evolution.tree.SimpleTree;
import dr.evolution.tree.Tree;
import dr.evolution.tree.TreeTrait;
import dr.evolution.tree.TreeTraitProvider;
import dr.evolution.util.Taxon;
import dr.evolution.util.Units;
import dr.inference.model.Parameter;
import dr.math.MathUtils;

/**
 * @author Arman D. Bilge
 */
public class CoevolutionSimulator {

	public CoevolutionSimulator() {} // Empty constructor, like the CoalescentSimulator
	
	/**
	 * Simulates a random coevolutionary process to generate a valid mapping for the starting tree.
	 * <p/>
	 * Truth be told, this hardly simulates coevolution, but instead does what is needed to generate a random, valid mapping.
	 * 
	 * @param hostTree
	 * @param symbiontTree
	 * @param hostAttributeName
	 */
	public void simulateCoevolution(Tree hostTree, MutableTree symbiontTree, CophylogenyLikelihood cophylogenyLikelihood, String hostAttributeName, boolean samplingNoHost) {
		
		for (int i = 0; i < symbiontTree.getExternalNodeCount(); ++i) {
			final NodeRef node = symbiontTree.getExternalNode(i);
			final Taxon hostTaxon = (Taxon) symbiontTree.getNodeTaxon(node).getAttribute(hostAttributeName);
			NodeRef hostNode = null;
			if (hostTaxon != null) {
				hostNode = hostTree.getExternalNode(hostTree.getTaxonIndex(hostTaxon.getId()));
			}
			cophylogenyLikelihood.setStatesForNode(node, hostNode);
		}

		for (int i = 0; i < symbiontTree.getInternalNodeCount(); ++i) {
		    final NodeRef node = symbiontTree.getInternalNode(i);
		    final List<NodeRef> contemporaneous = new ArrayList<NodeRef>(CophylogenyModel.Utils.getContemporaneousLineages(hostTree, symbiontTree.getNodeHeight(node)));
		    if (samplingNoHost) contemporaneous.add(null);
		    cophylogenyLikelihood.setStatesForNode(node, contemporaneous.get(MathUtils.nextInt(contemporaneous.size())));
		}
		
		cophylogenyLikelihood.setOriginHeight(hostTree.getNodeHeight(hostTree.getRoot()));
				
	}
	
	private double logLikelihood;
	public double getSimulationLogLikelihood() { return logLikelihood; }
	private int extinctSymbiontCount;
	private EventTrait eventTrait = new EventTrait();
	public final TreeTraitProvider provider = new TreeTraitProvider.Helper(eventTrait);
	public static final String COEVOLUTIONARY_EVENT = "coevolutionaryEvent";
	
	private class EventTrait implements TreeTrait<EventType> {
		
		@Override
		public Intent getIntent() {
			return Intent.NODE;
		}

		@Override
		public boolean getLoggable() {
			return true;
		}

		@Override
		public EventType getTrait(Tree t, NodeRef nr) {
			return (EventType) ((SimpleNode) nr).getAttribute(COEVOLUTIONARY_EVENT);
		}

		@Override
		public Class<?> getTraitClass() {
			return EventType.class;
		}

		@Override
		public String getTraitName() {
			return COEVOLUTIONARY_EVENT;
		}

		@Override
		public String getTraitString(Tree t, NodeRef nr) {
			return getTrait(t,nr).toString();
		}
				
	}

	public Tree simulateCoevolution(final Tree hostTree, final double originHeight, final double rate, final SimpleCophylogenyModel model, final boolean isRelaxed) {
	        return simulateCoevolution(hostTree, rate, model, isRelaxed, false);
	    }

	
	public Tree simulateCoevolution(final Tree hostTree, final double rate, final SimpleCophylogenyModel model, final boolean isRelaxed) {
		return simulateCoevolution(hostTree, rate, model, isRelaxed, false);
	}
	
	public Tree simulateCoevolution(final Tree hostTree, final double rate, final SimpleCophylogenyModel model, final boolean isRelaxed, final boolean keepExtinctions) {
		if (!isRelaxed)
			return simulateCoevolution(hostTree, rate, model, false, 0.0, keepExtinctions);
		throw new IllegalArgumentException();
	}

	   public Tree simulateCoevolution(final Tree hostTree, final double originHeight, final double rate, final SimpleCophylogenyModel model, final boolean isRelaxed, final boolean keepExtinctions) {
	        if (!isRelaxed)
	            return simulateCoevolution(hostTree, originHeight, rate, model, false, 0.0, keepExtinctions);
	        throw new IllegalArgumentException();
	    }
	
	public Tree simulateCoevolution(final Tree hostTree, final double rate, final SimpleCophylogenyModel model, final boolean isRelaxed, final double stdev) {
		return simulateCoevolution(hostTree, rate, model, isRelaxed, stdev, false);
	}

	   public Tree simulateCoevolution(final Tree hostTree, final double originHeight, final double rate, final SimpleCophylogenyModel model, final boolean isRelaxed, final double stdev, final boolean keepExtinctions) {
	        
	        SimpleNode root;
	        do {
	            logLikelihood = 0.0;
	            symbiontCounts = new int[hostTree.getTaxonCount()];
	            extinctSymbiontCount = 0;
	            associations.clear();
	            root = simulateCoevolution(hostTree,
	                    hostTree.getRoot(),
	                    originHeight,
	                    rate,
	                    model.getDuplicationRate(),
	                    model.getHostSwitchRate(),
	                    model.getLossRate(),
	                    isRelaxed,
	                    stdev,
	                    keepExtinctions);
	        } while (root == null);
	        return new SimpleTree(root);
	    }

	
	public Tree simulateCoevolution(final Tree hostTree, final double rate, final SimpleCophylogenyModel model, final boolean isRelaxed, final double stdev, final boolean keepExtinctions) {
		
		SimpleNode root;
		do {
			logLikelihood = 0.0;
			symbiontCounts = new int[hostTree.getTaxonCount()];
			extinctSymbiontCount = 0;
			associations.clear();
			root = simulateCoevolution(hostTree,
					hostTree.getRoot(),
					hostTree.getNodeHeight(hostTree.getRoot()) + MathUtils.nextExponential(hostTree.getNodeCount() / hostTree.getNodeHeight(hostTree.getRoot())),
					rate,
					model.getDuplicationRate(),
					model.getHostSwitchRate(),
					model.getLossRate(),
					isRelaxed,
					stdev,
					keepExtinctions);
		} while (root == null);
		return new SimpleTree(root);
	}
	
	public Tree simulateCoevolution(final Tree hostTree, final NodeRef origin, final double originHeight, final double rate, final SimpleCophylogenyModel model, final boolean isRelaxed, final double stdev) {

		symbiontCounts = new int[hostTree.getTaxonCount()];
		extinctSymbiontCount = 0;
		associations.clear();
		SimpleNode root = simulateCoevolution(hostTree,
				origin,
				originHeight,
				rate,
				model.getDuplicationRate(),
				model.getHostSwitchRate(),
				model.getLossRate()
				);
		return root == null ? null : new SimpleTree(root);
	}
	
	private SimpleNode simulateCoevolution(final Tree hostTree, final NodeRef hostNode, final double height, final double rate, final double duplicationRate, final double hostSwitchRate, final double lossRate, final boolean isRelaxed, final double stdev) {
	
		return simulateCoevolution(hostTree, hostNode, height, rate, duplicationRate, hostSwitchRate, lossRate, isRelaxed, stdev, false);
	
	}
	
	private int[] symbiontCounts;
	public final Map<String,String> associations = new HashMap<String,String>();
	private SimpleNode simulateCoevolution(final Tree hostTree, final NodeRef hostNode, final double height, final double rate, final double duplicationRate, final double hostSwitchRate, final double lossRate, final boolean isRelaxed, final double stdev, boolean keepExtinctions) {
				
		final SimpleNode node = new SimpleNode();
		
		final double relaxedRate;
		if (isRelaxed) {
			// Random lognormally distributed rate
			relaxedRate = Math.exp(rate + stdev * MathUtils.nextGaussian());
		} else {
			relaxedRate = rate;
		}
		
		node.setAttribute("cophylogeny.clock.rate", relaxedRate);
		node.setAttribute("host.nodeRef", hostNode.getNumber());
		
		final SimpleNode child1;
		final SimpleNode child2;
		
		final int nextEvent = nextPoissonEvent(rate * duplicationRate, rate * hostSwitchRate, rate * lossRate);
		final double eventHeight = height - nextPoissonEventTime(rate * duplicationRate, rate * hostSwitchRate, rate * lossRate);
		
		final double hostNodeHeight = hostTree.getNodeHeight(hostNode);
		if (hostNodeHeight > eventHeight) {
			node.setHeight(hostTree.getNodeHeight(hostNode));
			node.setAttribute(COEVOLUTIONARY_EVENT, EventType.NO_EVENT);
			if (hostTree.isExternal(hostNode)) {
				// Cannot coevolve anymore
				final String hostName = hostTree.getNodeTaxon(hostNode).getId();
				int i = Integer.parseInt(hostName.substring(4, hostName.length()));
				String taxonId = "symbiont" + i + "." + ++symbiontCounts[i - 1];
				node.setTaxon(new Taxon(taxonId));
				associations.put(taxonId,hostTree.getNodeTaxon(hostNode).getId());
				return node;
			}
			// Cospeciation event;
			child1 = simulateCoevolution(hostTree, hostTree.getChild(hostNode, 0), hostNodeHeight, rate, duplicationRate, hostSwitchRate, lossRate, isRelaxed, stdev, keepExtinctions);
			child2 = simulateCoevolution(hostTree, hostTree.getChild(hostNode, 1), hostNodeHeight, rate, duplicationRate, hostSwitchRate, lossRate, isRelaxed, stdev, keepExtinctions);
		} else {
			node.setHeight(eventHeight);
			switch(nextEvent) {
			case 0:
				// Duplication event
				node.setAttribute(COEVOLUTIONARY_EVENT, EventType.DUPLICATION);
				child1 = simulateCoevolution(hostTree, hostNode, eventHeight, rate, duplicationRate, hostSwitchRate, lossRate, isRelaxed, stdev, keepExtinctions);
				child2 = simulateCoevolution(hostTree, hostNode, eventHeight, rate, duplicationRate, hostSwitchRate, lossRate, isRelaxed, stdev, keepExtinctions);
				break;
			case 1:
				// Host-switch event
				NodeRef newHost;
				node.setAttribute(COEVOLUTIONARY_EVENT, EventType.HOST_SWITCH);
				if (!hostTree.isRoot(hostNode)) { // Can't host-switch if at the root!
					List<NodeRef> potentialNewHosts = new ArrayList<NodeRef>(Utils.getContemporaneousLineages(hostTree, eventHeight));
					if (!potentialNewHosts.remove(hostNode)) throw new RuntimeException("Contemporaneous lineages not working.");
					newHost = potentialNewHosts.get(MathUtils.nextInt(potentialNewHosts.size()));
					logLikelihood += Math.log(1 / potentialNewHosts.size());
					child1 = simulateCoevolution(hostTree, newHost,
							eventHeight, rate, duplicationRate, hostSwitchRate,
							lossRate, isRelaxed, stdev);
				} else {
					child1 = null; // Like a host-switch to a totally different tree that we're not following
				}
				child2 = simulateCoevolution(hostTree, hostNode, eventHeight, rate, duplicationRate, hostSwitchRate, lossRate, isRelaxed, stdev);
				break;
			case 2:
				node.setAttribute(COEVOLUTIONARY_EVENT, EventType.LOSS);
				if (keepExtinctions) { // Loss event; null indicates the lineage was lost
						String taxonId = "extinct_symbiont" + ++extinctSymbiontCount;
						node.setTaxon(new Taxon(taxonId));
						return node; } else { return null; }
			default: throw new RuntimeException("Unknown cophylogenetic event: " + nextEvent); // Shouldn't be needed
			}
		}
		
		if (child1 != null && child2 != null) {
			// Both lineages survived
			node.addChild(child1);
			node.addChild(child2);
		} else if (child1 == child2) {
			// Both are null, hence this entire lineage was lost
			return null;
		} else {
			// Just one child lineage is null/lost
			return child2 == null ? child1 : child2; // Set this node to the continuing child lineage
		}
		return node;
	}

	private SimpleNode simulateCoevolution(final Tree hostTree, final NodeRef hostNode, final double height, final double rate, final double duplicationRate, final double hostSwitchRate, final double lossRate) {
		
		final SimpleNode node = new SimpleNode();
				
		final SimpleNode child1;
		final SimpleNode child2;
		
		final int nextEvent = nextPoissonEvent(rate * duplicationRate, rate * hostSwitchRate, rate * lossRate);
		final double eventHeight = height - nextPoissonEventTime(rate * duplicationRate, rate * hostSwitchRate, rate * lossRate);
		
		final double hostNodeHeight = hostTree.getNodeHeight(hostNode);
		if (hostNodeHeight > eventHeight) {
			if (hostTree.isExternal(hostNode)) {
				// Cannot coevolve anymore
				return node;
			}
			// Cospeciation event;
			child1 = simulateCoevolution(hostTree, hostTree.getChild(hostNode, 0), hostNodeHeight, rate, duplicationRate, hostSwitchRate, lossRate);
			child2 = simulateCoevolution(hostTree, hostTree.getChild(hostNode, 1), hostNodeHeight, rate, duplicationRate, hostSwitchRate, lossRate);
		} else {
			switch(nextEvent) {
			case 0:
				// Duplication event
				child1 = simulateCoevolution(hostTree, hostNode, eventHeight, rate, duplicationRate, hostSwitchRate, lossRate);
				child2 = simulateCoevolution(hostTree, hostNode, eventHeight, rate, duplicationRate, hostSwitchRate, lossRate);
				break;
			case 1:
				// Host-switch event
				if (!hostTree.isRoot(hostNode)) { // Can't host-switch if at the root!
					List<NodeRef> potentialNewHosts = new ArrayList<NodeRef>(Utils.getContemporaneousLineages(hostTree, eventHeight));
					if (!potentialNewHosts.remove(hostNode)) throw new RuntimeException("Contemporaneous lineages not working.");
					NodeRef newHost = potentialNewHosts.get(MathUtils.nextInt(potentialNewHosts.size()));
					child1 = simulateCoevolution(hostTree, newHost, eventHeight, rate, duplicationRate, hostSwitchRate, lossRate);
				} else {
					child1 = null; // Like a host-switch to a totally different tree that we're not following
				}
				child2 = simulateCoevolution(hostTree, hostNode, eventHeight, rate, duplicationRate, hostSwitchRate, lossRate);
				break;
			case 2: return null;
			default: throw new RuntimeException("Unknown cophylogenetic event: " + nextEvent); // Shouldn't be needed
			}
		}
		
		if (child1 != null && child2 != null) {
			// Both lineages survived
			node.addChild(child1);
			node.addChild(child2);
		} else if (child1 == child2) {
			// Both are null, hence this entire lineage was lost
			return null;
		} else {
			// Just one child lineage is null/lost
			return child2 == null ? child1 : child2; // Set this node to the continuing child lineage
		}
		return node;
	}
	
	private final double nextPoissonEventTime(final double...lambdas) {
		final double lambda = MathUtils.getTotal(lambdas);
		return MathUtils.nextExponential(lambda);
	}
	
	private final int nextPoissonEvent(final double...lambdas) {
		final double lambda = MathUtils.getTotal(lambdas);
		final double[] p = new double[lambdas.length - 1];
		p[0] = lambdas[0] / lambda;
		for (int i = 1; i < p.length; ++i)
			p[i] = lambdas[i] / lambda + p[i - 1];
		final double U = 1 - MathUtils.nextDouble();
		int i;
		for (i = 0; i < p.length && p[i] < U; ++i);
		return i;
	}
				
	public static void main(String[] args) {
		
		Locale.setDefault(Locale.US);
		Arguments arguments = new Arguments(new Arguments.Option[]{
				new Arguments.RealOption("o", "time of origin for host"),
				new Arguments.RealArrayOption("h", 2, "host birth-death rates"), 
				new Arguments.RealArrayOption("r", 3, "coevolutionary rates"),
				new Arguments.RealOption("c", "relaxed clock stdev"),
				new Arguments.LongOption("seed", "random number generator seed")
		});
		
		try {
			arguments.parseArguments(args);
		} catch (ArgumentException e) {
			e.printStackTrace(System.err);
			arguments.printUsage("coevolutionsim", "");
			System.exit(1);
		}
	
		long seed = MathUtils.getSeed();
		if (arguments.hasOption("seed")) {
            seed = arguments.getLongOption("seed");
            if (seed <= 0) {
                System.err.println("The random number seed should be > 0");
                System.exit(1);
            }
    		MathUtils.setSeed(seed);
        }
		System.out.println("Seed used: " + seed);
		System.out.println("Rates: " + ArrayUtils.toString(arguments.getRealArrayOption("r")));
		if (arguments.hasOption("c")) System.out.println(arguments.getRealOption("c"));
		System.out.println();
		
		double[] rates = arguments.getRealArrayOption("h");
		final Tree hostTree = new BirthDeathSimulator().simulateBirthDeathTree(arguments.getRealOption("o"), rates[0], rates[1]);
		
		MutableTree mutableTree = (MutableTree) hostTree;
		for (int i = 0; i < mutableTree.getNodeCount(); ++i) {
			NodeRef node = mutableTree.getNode(i);
			mutableTree.setNodeAttribute(node, "nodeRef", node.getNumber());
		}
		
		PrintStream stream;
		File f;
		try {
			stream = new PrintStream(new FileOutputStream("host.tre"));
			new NexusExporter(stream).exportTree(hostTree);
			stream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		f = new File("host.newick");
		f.deleteOnExit();
		try {
			stream = new PrintStream(new FileOutputStream(f));
			stream.println(Tree.Utils.newick(hostTree));
			stream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		rates = arguments.getRealArrayOption("r");
		final SimpleCophylogenyModel scm = new SimpleCophylogenyModel(new Parameter.Default(rates[0]), new Parameter.Default(rates[1]), new Parameter.Default(1), Units.Type.YEARS);
		final CoevolutionSimulator cs = new CoevolutionSimulator();
		final Tree symbiontTree;
		if (arguments.hasOption("c")) {
			symbiontTree = cs.simulateCoevolution(hostTree, rates[2], scm, true, arguments.getRealOption("c"));
		} else {
			symbiontTree = cs.simulateCoevolution(hostTree, rates[2], scm, false);
		}
		
		try {
			stream = new PrintStream("symbiont.tre");
			new NexusExporter(stream).exportTree(symbiontTree);
			stream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		f = new File("symbiont.newick");
		f.deleteOnExit();
		try {
			stream = new PrintStream(new FileOutputStream(f));
			stream.println(Tree.Utils.newick(symbiontTree));
			stream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		try {
			stream = new PrintStream(new FileOutputStream("associations.map"));
			for (String key : cs.associations.keySet()) {
				stream.println(key + "\t" + cs.associations.get(key));
			}
			stream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		SeqGen.main(new String[]{"host.newick", "host", "0.1"});
		SeqGen.main(new String[]{"symbiont.newick", "symbiont", "0.1"});
		
	}
	
	public static void debugHelper(Tree hostTree, Tree symbiontTree, CophylogenyLikelihood cophylogenyLikelihood) {
		
		NodeRefProvider nrp = new NodeRefProvider(hostTree,"nodeRef");
		System.err.println(Tree.Utils.newick(hostTree, new TreeTraitProvider[]{nrp}));
		System.err.println(Tree.Utils.newick(symbiontTree, new TreeTraitProvider[]{cophylogenyLikelihood}));
		
	}
	
}
