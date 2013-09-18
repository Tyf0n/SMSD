/*
 *
 * Copyright (C) 2009-2013  Syed Asad Rahman <asad@ebi.ebi.ac.uk>
 *
 * Contact: cdk-devel@lists.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 * All we ask is that proper credit is given for our work, which includes
 * - but is not limited to - adding the above copyright notice to the beginning
 * of your source code files, and to any copyright notice that you may distribute
 * with programs based on this work.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received iIndex copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.openscience.smsd.algorithm.mcsplus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openscience.cdk.annotations.TestClass;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.isomorphism.matchers.IQueryAtom;
import org.openscience.cdk.isomorphism.matchers.IQueryBond;
import org.openscience.smsd.algorithm.matchers.DefaultMatcher;
import org.openscience.smsd.helper.LabelContainer;

/**
 * This class generates compatibility graph between query and target molecule. It also marks edges in the compatibility
 * graph as c-edges or d-edges. @cdk.module smsd @cdk.githash
 *
 * @author Syed Asad Rahman <asad@ebi.ac.uk>
 */
@TestClass("org.openscience.cdk.smsd.SMSDBondSensitiveTest")
public final class GenerateCompatibilityGraph {

    private List<Integer> compGraphNodes = null;
    private List<Integer> compGraphNodesCZero = null;
    private List<Integer> cEdges = null;
    private List<Integer> dEdges = null;
    private int cEdgesSize = 0;
    private int dEdgesSize = 0;
    private final IAtomContainer source;
    private final IAtomContainer target;
    private final boolean shouldMatchBonds;
    private final boolean shouldMatchRings;

    /**
     * Generates a compatibility graph between two molecules
     *
     * @param source
     * @param target
     * @param shouldMatchBonds
     * @param shouldMatchRings
     * @throws java.io.IOException
     */
    public GenerateCompatibilityGraph(
            IAtomContainer source,
            IAtomContainer target,
            boolean shouldMatchBonds,
            boolean shouldMatchRings) throws IOException {
        this.shouldMatchRings = shouldMatchRings;
        this.shouldMatchBonds = shouldMatchBonds;
        this.source = source;
        this.target = target;
        compGraphNodes = new ArrayList<>();
        compGraphNodesCZero = new ArrayList<>();
        cEdges = Collections.synchronizedList(new ArrayList<Integer>());
        dEdges = Collections.synchronizedList(new ArrayList<Integer>());
//        System.out.println("compatibilityGraphNodes ");
        compatibilityGraphNodes();
//        System.out.println("compatibilityGraph ");
        compatibilityGraph();

        if (getCEdgesSize() == 0) {
            clearCompGraphNodes();

            clearCEgdes();
            clearDEgdes();

            resetCEdgesSize();
            resetDEdgesSize();

            compatibilityGraphNodesIfCEdgeIsZero();
            compatibilityGraphCEdgeZero();
            clearCompGraphNodesCZero();
        }
    }

    private Map<IAtom, List<String>> labelAtoms(IAtomContainer atomCont) {
        Map<IAtom, List<String>> label_list = new HashMap<>();

        for (int i = 0; i < atomCont.getAtomCount(); i++) {
            List<String> label = new ArrayList<>(7);
            for (int a = 0; a < 7; a++) {
                label.add(a, "Z9");
            }
            IAtom refAtom = atomCont.getAtom(i);
            String referenceAtom = refAtom.getSymbol();

            label.set(0, referenceAtom);
            List<IAtom> connAtoms = atomCont.getConnectedAtomsList(refAtom);

            int counter = 1;

            for (IAtom negAtom : connAtoms) {
                String neighbouringAtom = negAtom.getSymbol();
                label.set(counter, neighbouringAtom);
                counter += 1;
            }
            bubbleSort(label);
            label_list.put(refAtom, label);
        }
        return label_list;
    }

    private void bubbleSort(List<String> num) {
        int j;
        boolean flag = true;   // set flag to true to begin first pass
        String temp;   //holding variable

        while (flag) {
            flag = false;    //set flag to false awaiting a possible swap
            for (j = 0; j < (num.size() - 1); j++) {
                if (num.get(j).compareTo(num.get(j + 1)) > 0) // change to < for descending sort
                {
                    temp = num.get(j);                //swap elements
                    num.set(j, num.get(j + 1));
                    num.set(j + 1, temp);
                    flag = true;              //shows a swap occurred  
                }
            }
        }
    }

    private boolean isSubset(List<String> labelA, List<String> labelB) {
        boolean flag = true;
        for (int i = 0; i < labelA.size(); i++) {
            if (!labelA.get(i).equals(labelB.get(i))) {
                if (labelA.get(i).compareTo(labelB.get(i)) > 0) {
                    flag = false;
                }
            }
        }
        return flag;
    }

    private boolean isEqual(List<String> labelA, List<String> labelB) {
        boolean flag = true;
        for (int i = 0; i < labelA.size(); i++) {
            if (!labelA.get(i).equals(labelB.get(i))) {
                if (labelA.get(i).compareTo(labelB.get(i)) != 0) {
                    flag = false;
                }
            }
        }
        return flag;
    }

    /**
     * Generate Compatibility Graph Nodes
     *
     * @return
     * @throws IOException
     */
    protected int compatibilityGraphNodes() throws IOException {

        compGraphNodes.clear();

        Map<IAtom, List<String>> label_list_molA = labelAtoms(source);
        Map<IAtom, List<String>> label_list_molB = labelAtoms(target);

        int nodeCount = 1;

        for (Map.Entry<IAtom, List<String>> labelA : label_list_molA.entrySet()) {
            for (Map.Entry<IAtom, List<String>> labelB : label_list_molB.entrySet()) {
                if (isSubset(labelA.getValue(), labelB.getValue())) {
//                    System.err.println("IS SUBSET");
                    if (isEqual(labelA.getValue(), labelB.getValue())) {
//                    System.err.println("IS EQUAL");
                        compGraphNodes.add(source.getAtomNumber(labelA.getKey()));
                        compGraphNodes.add(target.getAtomNumber(labelB.getKey()));
                        compGraphNodes.add(nodeCount);
                        nodeCount += 1;
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Generate Compatibility Graph Nodes Bond Insensitive
     *
     * @return
     * @throws IOException
     */
    protected int compatibilityGraph() throws IOException {

        int comp_graph_nodes_List_size = compGraphNodes.size();

//        System.out.println("Source atom count " + source.getAtomCount());
//        System.out.println("target atom count " + target.getAtomCount());
//        System.out.println("compGraphNodes combs: " + compGraphNodes.size());
//        System.out.println("compGraphNodes size " + comp_graph_nodes_List_size);
//        System.out.println("compGraphNodes " + compGraphNodes);
        cEdges = new ArrayList<>(); //Initialize the cEdges List
        dEdges = new ArrayList<>(); //Initialize the dEdges List

        for (int a = 0; a < comp_graph_nodes_List_size; a += 3) {
            for (int b = a; b < comp_graph_nodes_List_size; b += 3) {
                if ((a != b)
                        && (compGraphNodes.get(a) != compGraphNodes.get(b))
                        && (compGraphNodes.get(a + 1) != compGraphNodes.get(b + 1))) {

                    IBond reactantBond;
                    IBond productBond;

//                    System.out.println("a " + compGraphNodes.get(a) + " b " + compGraphNodes.get(b));
                    //exists a bond in molecule 2, so that molecule 1 pair is connected?
                    reactantBond = source.getBond(source.getAtom(compGraphNodes.get(a)), source.getAtom(compGraphNodes.get(b)));
                    productBond = target.getBond(target.getAtom(compGraphNodes.get(a + 1)), target.getAtom(compGraphNodes.get(b + 1)));

                    if (reactantBond != null && productBond != null) {
                        addEdges(reactantBond, productBond, a, b);
                    } else if (reactantBond == null && productBond == null) {
                        dEdges.add((a / 3) + 1);
                        dEdges.add((b / 3) + 1);
                    }
                }
            }
        }
        cEdgesSize = cEdges.size();
        dEdgesSize = dEdges.size();
        return 0;
    }

    private void addEdges(IBond reactantBond, IBond productBond, int iIndex, int jIndex) {

        if (isMatchFeasible(reactantBond, productBond, isMatchBond(), isMatchRings())) {
            cEdges.add((iIndex / 3) + 1);
            cEdges.add((jIndex / 3) + 1);
        } else {
            dEdges.add((iIndex / 3) + 1);
            dEdges.add((jIndex / 3) + 1);
        }
    }

    /**
     * compGraphNodesCZero is used to build up of the edges of the compatibility graph
     *
     * @return
     * @throws IOException
     */
    private Integer compatibilityGraphNodesIfCEdgeIsZero() throws IOException {

        int count_nodes = 1;
        List<String> map = new ArrayList<>();
        compGraphNodesCZero = new ArrayList<>(); //Initialize the compGraphNodesCZero List
        LabelContainer labelContainer = LabelContainer.getInstance();
        compGraphNodes.clear();

        for (int i = 0; i < source.getAtomCount(); i++) {
            for (int j = 0; j < target.getAtomCount(); j++) {
                IAtom atom1 = source.getAtom(i);
                IAtom atom2 = target.getAtom(j);

                //You can also check object equal or charge, hydrogen count etc
                if ((atom1 instanceof IQueryAtom)
                        && ((IQueryAtom) atom1).matches(atom2)
                        && !map.contains(i + "_" + j)) {
                    compGraphNodesCZero.add(i);
                    compGraphNodesCZero.add(j);
                    compGraphNodesCZero.add(labelContainer.getLabelID(atom2.getSymbol())); //i.e C is label 1
                    compGraphNodesCZero.add(count_nodes);
                    compGraphNodes.add(i);
                    compGraphNodes.add(j);
                    compGraphNodes.add(count_nodes);
                    count_nodes += 1;
                    map.add(i + "_" + j);
                } else if (atom1.getSymbol().equalsIgnoreCase(atom2.getSymbol())
                        && !map.contains(i + "_" + j)) {
                    compGraphNodesCZero.add(i);
                    compGraphNodesCZero.add(j);
                    compGraphNodesCZero.add(labelContainer.getLabelID(atom1.getSymbol())); //i.e C is label 1
                    compGraphNodesCZero.add(count_nodes);
                    compGraphNodes.add(i);
                    compGraphNodes.add(j);
                    compGraphNodes.add(count_nodes);
                    count_nodes += 1;
                    map.add(i + "_" + j);
                }
            }
        }
        map.clear();
        return count_nodes;
    }

    /**
     * compatibilityGraphCEdgeZero is used to build up of the edges of the compatibility graph BIS
     *
     * @return
     * @throws IOException
     */
    private int compatibilityGraphCEdgeZero() throws IOException {

        int compGraphNodesCZeroListSize = compGraphNodesCZero.size();
        cEdges = new ArrayList<>(); //Initialize the cEdges List
        dEdges = new ArrayList<>(); //Initialize the dEdges List

        for (int a = 0; a < compGraphNodesCZeroListSize; a += 4) {
            int index_a = compGraphNodesCZero.get(a);
            int index_aPlus1 = compGraphNodesCZero.get(a + 1);
            for (int b = a + 4; b < compGraphNodesCZeroListSize; b += 4) {
                int index_b = compGraphNodesCZero.get(b);
                int index_bPlus1 = compGraphNodesCZero.get(b + 1);

                // if element atomCont !=jIndex and atoms on the adjacent sides of the bonds are not equal
                if ((a != b) && (index_a != index_b)
                        && (index_aPlus1 != index_bPlus1)) {

                    IBond reactantBond;
                    IBond productBond;

                    reactantBond = source.getBond(source.getAtom(index_a), source.getAtom(index_b));
                    productBond = target.getBond(target.getAtom(index_aPlus1), target.getAtom(index_bPlus1));

                    if (reactantBond != null && productBond != null) {
                        addZeroEdges(reactantBond, productBond, a, b);
                    }
                }
            }
        }

        //Size of C and D edges of the compatibility graph
        cEdgesSize = cEdges.size();
        dEdgesSize = dEdges.size();
        return 0;
    }

    private void addZeroEdges(IBond reactantBond, IBond productBond, int indexI, int indexJ) {
        if (isMatchFeasible(reactantBond, productBond, isMatchBond(), isMatchRings())) {
            cEdges.add((indexI / 4) + 1);
            cEdges.add((indexJ / 4) + 1);
        }
        if (reactantBond == null && productBond == null) {
            dEdges.add((indexI / 4) + 1);
            dEdges.add((indexJ / 4) + 1);
        }
    }

    /**
     *
     * @param bondA1
     * @param bondA2
     * @param shouldMatchBonds
     * @param shouldMatchRings
     * @return
     */
    private boolean isMatchFeasible(
            IBond bondA1,
            IBond bondA2,
            boolean shouldMatchBonds,
            boolean shouldMatchRings) {

        if (bondA1 instanceof IQueryBond) {
            if (((IQueryBond) bondA1).matches(bondA2)) {
                IQueryAtom atom1 = (IQueryAtom) (bondA1.getAtom(0));
                IQueryAtom atom2 = (IQueryAtom) (bondA1.getAtom(1));
                return atom1.matches(bondA2.getAtom(0)) && atom2.matches(bondA2.getAtom(1))
                        || atom1.matches(bondA2.getAtom(1)) && atom2.matches(bondA2.getAtom(0));
            }
            return false;
        } else {
            return DefaultMatcher.matches(bondA1, bondA2, shouldMatchBonds, shouldMatchRings);
        }
    }

    public List<Integer> getCEgdes() {
        return Collections.unmodifiableList(cEdges);
    }

    public List<Integer> getDEgdes() {
        return Collections.unmodifiableList(dEdges);
    }

    public List<Integer> getCompGraphNodes() {
        return Collections.unmodifiableList(compGraphNodes);
    }

    protected int getCEdgesSize() {
        return cEdgesSize;
    }

    protected int getDEdgesSize() {
        return dEdgesSize;
    }

    private List<Integer> getCompGraphNodesCZero() {
        return Collections.unmodifiableList(compGraphNodesCZero);
    }

    private void clearCEgdes() {
        cEdges.clear();
    }

    private void clearDEgdes() {
        dEdges.clear();
    }

    private void clearCompGraphNodes() {
        compGraphNodes.clear();
    }

    private void clearCompGraphNodesCZero() {
        compGraphNodesCZero.clear();
    }

    private void resetCEdgesSize() {
        cEdgesSize = 0;
    }

    private void resetDEdgesSize() {
        dEdgesSize = 0;
    }

    public synchronized void clear() {
        cEdges = null;
        dEdges = null;
        compGraphNodes = null;
        compGraphNodesCZero = null;
    }

    /**
     * @return the shouldMatchBonds
     */
    private boolean isMatchBond() {
        return shouldMatchBonds;
    }

    /**
     * @return the shouldMatchRings
     */
    private boolean isMatchRings() {
        return shouldMatchRings;
    }
}
