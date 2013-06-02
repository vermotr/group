package group;

import java.util.Set;

import combinatorics.DisjointSetForest;

/**
 * Refines vertex partitions until they are discrete, and therefore equivalent
 * to permutations. These permutations are automorphisms of the graph that was
 * used during the refinement to guide the splitting of partition blocks. 
 * 
 * @author maclean
 * @cdk.module group
 */
public abstract class AbstractDiscretePartitionRefiner {
    
    public enum Result { WORSE, EQUAL, BETTER };
    
    private boolean bestExist;
    
    private Permutation best;
    
    private Permutation first;
    
    private IEquitablePartitionRefiner equitableRefiner;
    
    private SSPermutationGroup group;
    
    private boolean checkVertexColors;
    
    public AbstractDiscretePartitionRefiner() {
        this(false);
    }
    
    public AbstractDiscretePartitionRefiner(boolean checkVertexColors) {
        this.checkVertexColors = checkVertexColors;
        this.bestExist = false;
        this.best = null;
        this.equitableRefiner = null;
    }
    
    public abstract int getVertexCount();
    
    public abstract boolean isConnected(int i, int j);
    
    public abstract boolean sameColor(int i, int j);
    
    public void setup(SSPermutationGroup group, IEquitablePartitionRefiner refiner) {
        this.bestExist = false;
        this.best = null;
        this.group = group;
        this.equitableRefiner = refiner;
    }
    
    public boolean firstIsIdentity() {
        return this.first.isIdentity();
    }
    
    public long getCertificate() {
        return calculateCertificate(this.getBest());
    }
    
    public long calculateCertificate(Permutation p) {
        int k = 0;
        long certificate = 0;
        int n = getVertexCount();
        for (int j = n - 1; j > 0; j--) {
        	for (int i = j - 1; i >= 0; i--) {
        		if (isConnected(p.get(i), p.get(j))) {
        			certificate += (int)Math.pow(2, k);
        		}
        		k++;
        	}
        }
//        for (int i = 0; i < n - 1; i++) {
//            for (int j = i + 1; j < n; j++) {
//                if (isConnected(p.get(i), p.get(j))) {
//                    certificate += (int)Math.pow(2, k);
//                }
//                k++;
//            }
//        }
        return certificate;
    }
    
    public String getHalfMatrixString(Permutation p) {
        String hms = "";
        int n = p.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 1; j < n; j++) {
                if (isConnected(p.get(i), p.get(j))) {
                    hms += "1";
                } else {
                    hms += "0";
                }
            }
        }
        return hms;
    }
    
    public String getBestHalfMatrixString() {
       return getHalfMatrixString(best);
    }
    
    public String getFirstHalfMatrixString() {
        return getHalfMatrixString(first);
     }
    
    public String getHalfMatrixString() {
        return getHalfMatrixString(new Permutation(getVertexCount()));
    }
    
    public SSPermutationGroup getGroup() {
        return this.group;
    }
    
    public Permutation getBest() {
        return this.best;
    }
    
    public Permutation getFirst() {
        return this.first;
    }
    
    /**
     * The automorphism partition is a partition of the elements of the group.
     * 
     * @return a partition of the elements of group 
     */
    public Partition getAutomorphismPartition() {
        final int n = group.getSize();
        final DisjointSetForest forest = new DisjointSetForest(n);
        group.apply(new SSPermutationGroup.Backtracker() {

            boolean[] inOrbit = new boolean[n];
            private int inOrbitCount = 0;
            private boolean isFinished;

            @Override
            public boolean finished() {
                return isFinished;
            }

            @Override
            public void applyTo(Permutation p) {
                for (int elementX = 0; elementX < n; elementX++) {
                    if (inOrbit[elementX]) {
                        continue;
                    } else {
                        int elementY = p.get(elementX);
                        while (elementY != elementX) {
                            if (!inOrbit[elementY]) {
                                inOrbitCount++;
                                inOrbit[elementY] = true;
                                forest.makeUnion(elementX, elementY);
                            }
                            elementY = p.get(elementY);
                        }
                    }
                }
                if (inOrbitCount == n) {
                    isFinished = true;
                }
            }
        });

        // convert to a partition
        Partition partition = new Partition();
        for (int[] set : forest.getSets()) {
            partition.addCell(set);
        }

        // necessary for comparison by string
        partition.order();
        return partition;
    }

    
    /**
     * Check for a canonical graph, without generating the whole 
     * automorphism group.
     * 
     * @return true if the graph is canonical
     */
    public boolean isCanonical() {
        return isCanonical(Partition.unit(getVertexCount()));
    }
    
    public boolean isCanonical(Partition partition) {
        int n = getVertexCount();
        if (partition.size() == n) {
            return partition.toPermutation().isIdentity();
        } else {
            int l = partition.getIndexOfFirstNonDiscreteCell();
            int first = partition.getFirstInCell(l);
            Partition finerPartition = 
                equitableRefiner.refine(partition.splitBefore(l, first));
            return isCanonical(finerPartition);
        }
    }
    
    public void refine(Partition p) {
        refine(this.group, p);
    }
    
    public void refine(SSPermutationGroup group, Partition coarser) {
//    	System.out.println(coarser);
        int vertexCount = getVertexCount();
        
        Partition finer = equitableRefiner.refine(coarser);
        
        int firstNonDiscreteCell = finer.getIndexOfFirstNonDiscreteCell();
        if (firstNonDiscreteCell == -1) {
            firstNonDiscreteCell = vertexCount;
        }
        
        Permutation pi1 = new Permutation(firstNonDiscreteCell);
        Permutation pi2 = new Permutation(vertexCount);
        
        Result result = Result.BETTER;
        if (bestExist) {
            finer.setAsPermutation(pi1, firstNonDiscreteCell);
            result = compareRowwise(pi1);
        }
        
        if (finer.size() == vertexCount) {    // partition is discrete
//        	System.out.println("Disc :\t" + finer + "\t" + result);
            if (!bestExist) {
                best = finer.toPermutation();
                first = finer.toPermutation();
                bestExist = true;
            } else {
                if (result == Result.BETTER) {
                    best = new Permutation(pi1);
                } else if (result == Result.EQUAL) {
                    pi2 = pi1.multiply(best.invert());
                    if (!checkVertexColors || colorsAutomorphic(pi2)) {
                        group.enter(pi2);
                    }
                }
            }
        } else {
            if (result != Result.WORSE) {
                Set<Integer> blockCopy = finer.copyBlock(firstNonDiscreteCell);
                for (int vertexInBlock = 0; vertexInBlock < vertexCount; vertexInBlock++) {
                    if (blockCopy.contains(vertexInBlock)) {
                        Partition nextPartition = 
                            finer.splitBefore(firstNonDiscreteCell, vertexInBlock);
                        
                        this.refine(group, nextPartition);
                        
                        Permutation permF = new Permutation(vertexCount);
                        Permutation invF = new Permutation(vertexCount);
                        
                        for (int j = 0; j <= firstNonDiscreteCell; j++) {
                            int x = nextPartition.getFirstInCell(j);
                            int i = invF.get(x);
                            int h = permF.get(j);
                            permF.set(j, x);
                            permF.set(i, h);
                            invF.set(h, i);
                            invF.set(x, j);
                        }
                        group.changeBase(permF);
                        for (int j = 0; j < vertexCount; j++) {
                            Permutation g = group.get(firstNonDiscreteCell, j);
                            if (g != null) blockCopy.remove(g.get(vertexInBlock));
                        }
                    }
                }
            }
        }
    }
    
    private boolean colorsAutomorphic(Permutation p) {
        for (int i = 0; i < p.size(); i++) {
            if (sameColor(i, p.get(i))) {
                continue;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Check a permutation to see if it is better, equal, or worse than the 
     * current best.
     * 
     * @param perm the permutation to check
     * @return BETTER, EQUAL, or WORSE
     */
    public Result compareColumnwise(Permutation perm) {
        int m = perm.size();
        for (int i = 1; i < m; i++) {
            for (int j = 0; j < i; j++) {
                int x = isAdjacent(best.get(i), best.get(j));
                int y = isAdjacent(perm.get(i), perm.get(j));
                if (x > y) return Result.WORSE;
                if (x < y) return Result.BETTER;
            }
        }
        return Result.EQUAL;
    }
    
    public Result compareRowwise(Permutation perm) {
        int m = perm.size();
        for (int i = 0; i < m - 1; i++) {
            for (int j = i + 1; j < m; j++) {
                int x = isAdjacent(best.get(i), best.get(j));
                int y = isAdjacent(perm.get(i), perm.get(j));
                if (x > y) return Result.WORSE;
                if (x < y) return Result.BETTER;
            }
        }
        return Result.EQUAL;
    }

    private int isAdjacent(int i, int j) {
        if (isConnected(i, j)) { 
            return 1; 
        } else {
            return 0;
        }
    }

}
