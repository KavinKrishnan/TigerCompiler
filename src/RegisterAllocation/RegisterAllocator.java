package RegisterAllocation;

import javafx.beans.binding.IntegerBinding;
import sun.awt.image.ImageWatched;

import java.util.*;
import java.util.regex.Pattern;
import java.awt.*;

public class RegisterAllocator {


    public class CFGblock {
        public LinkedList<String> blockLines;
        public int firstBlockLine;  //First Line in actual Program
        public LinkedList<CFGblock> children; //Succesor CFG Blocks
        public Set<String> in;
        public HashMap<String, Integer> registerUses;
    }

    public class webIRLine {
        public String line;
        boolean isLead;   //Keeps track of branches
        boolean isLeadBlock; //Keeps track of Lead of Blocks

        public webIRLine() {
            isLead = false;
            isLeadBlock = false;
        }
    }

    public class varRange {
        int high;
        int low;
    }

    public final int numberOfMipsRegs = 41;

    public LinkedList<CFGblock> CFGblocks = new LinkedList<CFGblock>();

    public HashMap<String, Integer> publicVariableToRegister;
    public String lastLabel = "";
    public RegisterAllocator(ArrayList<String> IRcode) {

        ArrayList<webIRLine> webLines = new ArrayList<webIRLine>();

        HashMap<String,String> variableWebMatch = new HashMap<String, String>();

        HashMap<Integer, String> webToActualVar = new HashMap<Integer, String>();

        //Web Creation and
        //Code Block Creation

        //Goes through IR code and creates code blocks and does work for
        //Generating individual webs
        int webCounter = 0;

        //Web Line Creator
        for (int i = 0; i < IRcode.size(); i++){

            String s = IRcode.get(i);

            String[] parts = s.split(",\\s*");

            if (parts[0] == "assign") {

                if (variableWebMatch.containsKey(parts[1])) {

                    String[] webAndLineNum = variableWebMatch.get(parts[1]).split("_");
                    webToActualVar.put((Integer.parseInt(webAndLineNum[0])), parts[0] + "!" + webAndLineNum[1]);
                }

                variableWebMatch.put(parts[1], (webCounter++)+"_"+i);

            }


            //Get the Web Location
            if(!isReturn(parts[0])) {

                if (!Pattern.compile("^(?=.*\\d)\\d*(?:\\.\\d\\d)?$").matcher(parts[1]).find()) {
                    parts[1] = variableWebMatch.get(parts[1]).split("_")[0];
                }

                if (!Pattern.compile("^(?=.*\\d)\\d*(?:\\.\\d\\d)?$").matcher(parts[2]).find()) {
                    parts[2] = variableWebMatch.get(parts[2]).split("_")[0];
                }

                if (parts.length > 3 && isBranch(parts[0])){
                    parts[3] = variableWebMatch.get(parts[3].split("_")[0]);
                }
            }

            String webString = "";

            for (String str: parts) {
                webString += str + ", ";
            }


            webIRLine newWebIRLLine = new webIRLine();
            newWebIRLLine.isLead = isNewBlock(parts[0]);
            newWebIRLLine.line = webString;


            webLines.add(newWebIRLLine);

        }


        //------ Creating CFG --------

        CFGblock head = new CFGblock();

        //First line is always lead
        head.firstBlockLine = 0;
        head.blockLines.add(webLines.get(0).line);
        CFGblocks.add(head);

        CFGblock curr = CFGblocks.get(0);

        HashMap<String, HashSet<Integer>> branchParents = new HashMap<String, HashSet<Integer>>();

        for (int i = 1; i < webLines.size(); i++) {
            webIRLine currWIL = webLines.get(0);

            if (Pattern.compile(":").matcher(currWIL.line).find()){
                lastLabel = currWIL.line;
            }

            if (currWIL.isLeadBlock == true) {
                CFGblock temp = curr;
                curr = new CFGblock();
                curr.firstBlockLine = i;
                temp.children.add(curr);

                if (branchParents.containsKey(lastLabel)) {

                    HashSet<Integer> parents = branchParents.get(lastLabel);

                    for(Object p: parents.toArray()) {
                        CFGblocks.get((int)p).children.add(curr);
                    }
                }
            }


            if (currWIL.isLead) {

                //Line following a branch or return is a lead
                webLines.get(0).isLeadBlock = true;


                //Determine Target Leads and keep note in branchParents
                String targetLabel = currWIL.line.split(", ")[2];
                branchParents.get(targetLabel).add(branchParents.size()-1);
            }


            curr.blockLines.add(currWIL.line);

        }



        //--- Liveness Analysis ---

        //1 + the number of lines in the code is the number of codepoints to keep track of live variables
        HashSet<String>[] codepoints = new HashSet[IRcode.size() + 1];

        CFGblock currentOperatingCFGBlock = CFGblocks.get(CFGblocks.size() - 1);
        HashSet<String> lastIn = new HashSet<String>();

        HashMap<String, Integer> spillCosts = new  HashMap<String, Integer>();

        //Can skip the first one because the first one is uninitialized and we dont have any live variables yet
        for (int cp = codepoints.length - 2; cp >= 0; cp--) {

            if (cp -  1 < currentOperatingCFGBlock.firstBlockLine) {

                for (int c = CFGblocks.size() - 1; c >= 0; c--) {
                    CFGblock cf = CFGblocks.get(c);
                    if (cf.firstBlockLine <=  cp - 1) {
                        currentOperatingCFGBlock = cf;
                        break;
                    }
                }

                lastIn.clear();

                for (CFGblock cf: currentOperatingCFGBlock.children) {
                    lastIn.addAll(cf.in);
                }

            }

            Set<String> defined = new HashSet<String>();
            Set<String> used  = new HashSet<String>();

            getDefineAndUse(defined, used);

            for (String reg: used) {

                if (currentOperatingCFGBlock.registerUses.containsKey(reg)) {
                    int incUse = currentOperatingCFGBlock.registerUses.get(reg) + 1;
                    currentOperatingCFGBlock.registerUses.put(reg, incUse);
                } else {
                    currentOperatingCFGBlock.registerUses.put(reg, 1);
                }

                if (spillCosts.containsKey(reg)) {
                    int incUse = spillCosts.get(reg) + 1;
                    spillCosts.put(reg, incUse);
                } else {
                    spillCosts.put(reg, 1);
                }
            }


            HashSet<String> oldIn = lastIn;

            //In = (out - def) U used
            for (String str: defined) {
                if(lastIn.contains(str)) {
                    lastIn.remove(str);
                }
            }

            lastIn.addAll(used);


            if (cp == currentOperatingCFGBlock.firstBlockLine) {
                currentOperatingCFGBlock.in = lastIn;
            }

            codepoints[cp].addAll(lastIn);

        }

        //Live Range Calculator
        HashMap<String, Integer> rangeCalc =  new HashMap<String, Integer>();
        HashMap<String, varRange> varRanges = new HashMap<String, varRange>();

        for (int cp = 0; cp < codepoints.length; cp++) {

            for (String reg: rangeCalc.keySet()) {

                if (!codepoints[cp].contains(reg)) {
                    varRange newRange = new varRange();
                    newRange.low = rangeCalc.get(reg);
                    newRange.high = cp;
                    rangeCalc.remove(reg);
                    varRanges.put(reg, newRange);
                }

            }

            for (String reg: codepoints[cp]) {
                if (!rangeCalc.containsKey(reg) && !varRanges.containsKey(reg)) {
                    rangeCalc.put(reg, cp);
                }
            }

        }


        //Interference Edge Decider

        HashMap<String, HashSet<String>> Edges = new HashMap<String, HashSet<String>>();
        for (String u: varRanges.keySet()) {
            for(String v: varRanges.keySet()) {
                if (!Edges.containsKey(v)) {

                    varRange uRange = varRanges.get(u);
                    varRange vRange = varRanges.get(v);

                    if (((vRange.high <= uRange.high)  && (vRange.high >=uRange.low)) ||  ((vRange.low <= uRange.high)  && (vRange.low >= uRange.low))) {
                        Edges.get(u).add(v);
                    }

                }
            }
        }

        //  Briggs’ style graph coloring allocator

        Stack<String> regsLessThanN = new Stack<String>();
        Stack<String> regsGEQN = new Stack<String>();

        int regsAvl = numberOfMipsRegs;

        //Add all regs with degree less than N
        for (String reg: Edges.keySet()) {
            HashSet<String> initEdges = Edges.get(reg);
            initEdges.removeAll(regsGEQN);

            if (initEdges.size() < regsAvl) {
                regsLessThanN.add(reg);
            }
        }
        for (String reg: Edges.keySet()) {
            HashSet<String> initEdges = Edges.get(reg);
            initEdges.removeAll(regsGEQN);

            if (initEdges.size() < regsAvl) {
                regsLessThanN.add(reg);
            }
        }

        //We are left with reg greater and equal to N on
        for (String reg: Edges.keySet()) {
            if (!(regsGEQN.contains(reg) || regsLessThanN.contains(reg))) {
                regsGEQN.add(reg);
            }
        }


        //Puts them in order of spill cost
        regsGEQN.sort(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return spillCosts.get(o1) - spillCosts.get(o2);
            }
        });


        HashSet<String> spilledWebs = new  HashSet<String>();
        HashMap<String, Integer> currWebsToVariables = new HashMap<String, Integer>();


        //0..N-1 represents the different registers
        while (!regsGEQN.empty()) {
            String currSpill = regsGEQN.pop();
            if (regsAvl > 0) {
                currWebsToVariables.put(currSpill, --regsAvl);
            } else {
                currWebsToVariables.put(currSpill, -1);
                spilledWebs.add(currSpill);
            }
        }

        //Handle the ones that have a solution that are less than N
        HashMap<String, Integer> mappedVars = colorTheRest(regsLessThanN,  currWebsToVariables,  spilledWebs,  Edges , numberOfMipsRegs);


        for(String reg: mappedVars.keySet()) {
            publicVariableToRegister.put(webToActualVar.get(reg), mappedVars.get(reg));
        }

    }

    public HashMap<String, Integer> colorTheRest( Stack<String> regsLessThanN, HashMap<String, Integer> currWebsToVariables, HashSet<String> spilledWebs, HashMap<String, HashSet<String>> Edges , int N) {
        while(!regsLessThanN.isEmpty()) {
            String currentWeb = regsLessThanN.pop();
            for (int c = 0; c < N; c++) {
                boolean satisfied = true;
                for (String v: Edges.get(currentWeb)) {
                    if (!spilledWebs.contains(v)) {
                        if (currWebsToVariables.get(v) == c) {
                            satisfied = false;
                            break;
                        }
                    }
                }

                if (satisfied) {
                    currWebsToVariables.put(currentWeb, c);
                    break;
                }
            }
        }

        return currWebsToVariables;

    }
    
    public  LinkedList<CFGblock> maxUseAllocator(){
        return CFGblocks;
    }


    public  HashMap<String, Integer> coloringAllocator() {
        return publicVariableToRegister;
    }


    public void getDefineAndUse(Set<String> defined, Set<String> used){};


    public boolean isJumpTarget(String s) {

        return s.equals("goto");
    }

    public boolean isBranch(String s) {

        return s.equals("brgeq") || s.equals("brgt") || s.equals("breq") || s.equals("brleq") ||
                s.equals("brneq") || s.equals("brlt");
    }

    public boolean isReturn(String s) {
        return s.equals("return");
    }

    public boolean isNewBlock(String s) {
        return isJumpTarget(s) || isBranch(s) || isReturn(s);
    }





}
