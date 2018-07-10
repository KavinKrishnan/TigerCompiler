import com.sun.javafx.fxml.expression.Expression;
import com.sun.security.auth.NTDomainPrincipal;
import jdk.nashorn.internal.parser.Token;

import java.lang.reflect.Array;
import java.util.Stack;
import java.util.ArrayList;

public class TigerParser {
    private TigerScanner scanner;
    private NonTerminal[] nonTerminals;
    private int[][] parserTable;
    private SymbolTable symbolTable;
    private boolean success;
    public TigerIRGenerator ir;

    private boolean inFunction;
    private NTID previousState;
    private ArrayList<NTID> stateHistory;

    private ArrayList<NTID> stateHitsory;
    private class DerivationItem {
        private TokenEnum token;
        private NTID nonTerminal;
        private boolean isToken; // else non-terminal

        private DerivationItem(TokenEnum token) {
            this.token = token;
            isToken = true;
        }

        private DerivationItem(NTID nonTerminal) {
            this.nonTerminal = nonTerminal;
            isToken = false;
        }
    }

    private class NonTerminal {
        private ArrayList<DerivationItem>[] derivations;

        private NonTerminal(ArrayList<DerivationItem>[] derivations) {
            this.derivations = derivations;
        }

        private ArrayList<DerivationItem> getDerivation(int n) {
            return derivations[n];
        }
    }

    private enum NTID {
        TP, DS, TDL, VDL, FDL, TD, T, FL, TID, VD,
        IDL, IDLp, OI, FD, PL, PLT, RT, P, SS, SSp,
        S, Sp, Spp, Sppp, Spppp, E, E1, E1p, E2, E2p,
        E3, E3p, E4, E4p, E5, E5p, E6, C, EL, ELT,
        LV, LVT
    }

    private class DerivationState {
        private NTID derivation;
        private SymbolEntry entry;

        // for declaration
        private ArrayList<String> varIDs;
        private ArrayList<String> varTypes;
        private String typeid;
        private String funcid;
        private boolean getType;
        private boolean getRecordIDs;
        private boolean getArgs;
        private boolean getRetType;

        // for assignment/expression
        private String lvalue;
        private boolean getRecordVal;
        private String recordLvalue2;
        private boolean acceptInt;
        private boolean acceptFloat;
        private int isArray;
        private int arrIndex;
        private boolean isRight;


        private DerivationState(NTID derivation) {
            this.derivation = derivation;
        }

    }

    public boolean parse() {
        Stack<DerivationItem> stack = new Stack<>();
        Stack<DerivationState> stateStack = new Stack<>();
        ir = new TigerIRGenerator();
        // push end-of-input and <tiger-program> onto the stack
        stack.push(new DerivationItem(TokenEnum.ENDOFINPUT));
        stack.push(new DerivationItem(NTID.TP));
        stateStack.push(new DerivationState(NTID.TP));
        symbolTable.addLevel();

        // TODO: Add all library methods to symbol table
        FuncEntry libEntry = new FuncEntry("printi", "", null);
        symbolTable.addEntry(libEntry);

        TigerToken token = scanner.getNext();
        while (!stack.isEmpty()) {

            // pop the next token/non-terminal from the stack
            DerivationItem item = stack.pop();

            if (item.isToken) {         // the popped value is a token

                // check if next token from the scanner matches the expected token
                if (token.getToken() != item.token) {
                    success = false;
                    System.out.println();
                    System.out.println("Parser error (line " + token.getLine() + "): \"" + token.getLit() + "\" is not a valid token. Expected " + item.token.name());

                    // if we have semicolon or end-of-input token then leave current statement and continue
                    if (token.getToken() == TokenEnum.SEMI) {
                        while (stateStack.peek().derivation == NTID.E) {
                            stateStack.pop();
                        }
                        stateStack.pop();
                        while (!stack.isEmpty() && stack.peek().token != TokenEnum.SEMI) {
                            stack.pop();
                        }
                        if (!stack.isEmpty()) {
                            stack.pop();
                        } else {
                            break;
                        }
                    } else if (token.getToken() == TokenEnum.ENDOFINPUT) {
                        break;
                    } else {
                        stack.push(item);
                    }
                    token = scanner.getNext();
                } else {

                    // check semantics/update symbol table
                    if (!stateStack.isEmpty()) {
                        tokenSemantics(token, stateStack); //returns false if semantic error btw

                        //TODO ir generation
                        if (!stateStack.empty()) {
                            if (stateHistory.isEmpty()) {
                                stateHistory.add(stateStack.peek().derivation);
                            }
                            if (stateHistory.get(stateHistory.size() - 1) != stateStack.peek().derivation) {
                                stateHistory.add(stateStack.peek().derivation);
                            }

                            if (stateStack.peek().derivation == NTID.VD) {
                                ir.genVariables(symbolTable, token);
                            }

                        }
                    }

                    // get the next token unless we already got the last one
                    if (token.getToken() != TokenEnum.ENDOFINPUT) {
                        token = scanner.getNext();
                    }
                }

            } else {                    // the popped value is a non-terminal

                // get the derivation number that should be used for the next token
                int derivationNumber = parserTable[item.nonTerminal.ordinal()][token.getToken().ordinal()];

                if (derivationNumber < 0) {         // token doesn't match any derivation

                    success = false;
                    System.out.println();
                    System.out.print("Parser error (line " + token.getLine() + "): \"" + token.getLit() + "\" is not a valid token."
                            + " Expected any of the following tokens:");
                    for (int i = 0; i < parserTable[item.nonTerminal.ordinal()].length; ++i) {
                        if (parserTable[item.nonTerminal.ordinal()][i] >= 0) {
                            System.out.print(" " + TokenEnum.values()[i]);
                        }
                    }
                    System.out.println();

                    // if we have semicolon or end-of-input token then leave current statement and continue
                    if (token.getToken() == TokenEnum.SEMI) {
                        while (stateStack.peek().derivation == NTID.E) {
                            stateStack.pop();
                        }
                        stateStack.pop();
                        while (!stack.isEmpty() && stack.peek().token != TokenEnum.SEMI) {
                            stack.pop();
                        }
                        if (!stack.isEmpty()) {
                            stack.pop();
                        } else {
                            break;
                        }
                    } else if (token.getToken() == TokenEnum.ENDOFINPUT) {
                        break;
                    } else {
                        stack.push(item);
                    }
                    token = scanner.getNext();

                } else {                            // token matches some derivation

                    // check semantics/update symbol table
                    nonTerminalSemantics(item, stateStack, derivationNumber); //returns false if semantic error btw

                    //TODO ir generation

                   // if (stateStack.peek().derivation == NTID.E1p) {
                     //   System.out.println("Will pop");
                    //}
                    // now just put appropriate derivation onto the stack


                    ArrayList<DerivationItem> derivation = nonTerminals[item.nonTerminal.ordinal()].getDerivation(derivationNumber);

                    // push all tokens and terminals from that derivation onto the stack
                    for (int i = derivation.size() - 1; i >= 0; --i) {
                        stack.push(derivation.get(i));
                        //System.out.println("PUSHING: " + stack.peek().nonTerminal + "  " + stack.peek().token);
                    }
                }
            }
        }

        // We correctly matched all the tokens
        System.out.println();
        if (success) {
            System.out.println("successful parse");
        } else {
            System.out.println("unsuccessful parse");
        }
        return success;
    }

    private boolean tokenSemantics(TigerToken token, Stack<DerivationState> stateStack) {

        // SEMANTIC LOGIC

        DerivationState state = stateStack.peek();

        SymbolEntry entry = state.entry;

        if ((state.derivation == NTID.TP || state.derivation == NTID.FD)
                && token.getToken() == TokenEnum.END) {
            stateStack.pop();
            symbolTable.removeLevel();
        }
        if (state.derivation == NTID.TD) {
            if (token.getToken() == TokenEnum.ID) {
                if (!state.getType && !state.getRecordIDs) {
                    entry = new TypeEntry(token.getLit());
                    symbolTable.addEntry(entry);
                    state.getType = true;
                } else if (!state.getRecordIDs) {
                    if (symbolTable.contains(token.getLit(), SymbolEntry.SymbolType.TYPE)) {
                        entry.setType(token.getLit());
                    } else {
                        System.out.println("\nSemantic error (line " + token.getLine() + "): \"" + token.getLit() + "\" type was not declared");

                        success = false;
                    }
                } else {
                    if (!((TypeEntry) entry).recordNames.contains(token.getLit())) {
                        ((TypeEntry) entry).recordNames.add(token.getLit());
                    } else {
                        System.out.println("\nSemantic error (line " + token.getLine() + "): \"" + token.getLit() + "\" is already declared in this record");

                        success = false;
                    }
                }
            }
            if (token.getToken() == TokenEnum.INT) {
                if (!state.getRecordIDs) {
                    entry.setType(token.getLit());
                } else {
                    ((TypeEntry) entry).isIntFields.add(true);
                }
            }
            if (token.getToken() == TokenEnum.FLOAT) {
                if (!state.getRecordIDs) {
                    entry.setType(token.getLit());
                } else {
                    ((TypeEntry) entry).isIntFields.add(false);
                }
            }
            if (token.getToken() == TokenEnum.ARRAY) {
                entry.setType(token.getLit());
            }
            if (token.getToken() == TokenEnum.RECORD) {
                entry.setType(token.getLit());
                state.getRecordIDs = true;
                ((TypeEntry) entry).recordNames = new ArrayList<>();
                ((TypeEntry) entry).isIntFields = new ArrayList<>();
            }
            if (token.getToken() == TokenEnum.INTLIT) {
                ((TypeEntry) entry).arrSize = Integer.parseInt(token.getLit());
            }
            if (token.getToken() == TokenEnum.SEMI) {
                if (!state.getRecordIDs) {
                    stateStack.pop();
                }
            }
            if (token.getToken() == TokenEnum.END) {
                if (state.getRecordIDs) {
                    stateStack.pop();
                }
            }
        }
        if (state.derivation == NTID.FD) {
            if (token.getToken() == TokenEnum.ID || token.getToken() == TokenEnum.INT || token.getToken() == TokenEnum.FLOAT) {
                if (!state.getArgs && !state.getRetType) {
                    state.funcid = token.getLit();
                    state.getArgs = true;
                    ir.addToIR(state.funcid + ":");
                } else if (!state.getRetType) {
                    if (!state.getType) {
                        state.varIDs.add(token.getLit());
                        state.getType = true;
                    } else {
                        state.varTypes.add(token.getLit());
                        state.getType = false;
                    }
                } else {
                    state.typeid = token.getLit();
                }
            }
            if (token.getToken() == TokenEnum.RPAREN) {
                state.getRetType = true;
            }
            if (token.getToken() == TokenEnum.BEGIN) {
                if (state.typeid == null) {
                    state.typeid = "";
                }
                TypeEntry type = (TypeEntry) symbolTable.get(state.typeid, SymbolEntry.SymbolType.TYPE);
                if (type == null && !state.typeid.equals("int") && !state.typeid.equals("float") && !state.typeid.equals("")) {
                    System.out.println("\nSemantic error (line " + token.getLine() + "): \"" + state.typeid + "\" type was not declared");

                    success = false;
                }
                entry = new FuncEntry(state.funcid, state.typeid, type);
                symbolTable.addEntry(entry);
                symbolTable.addLevel();
                for (int i = 0; i < state.varIDs.size(); ++i) {
                    TypeEntry varType = (TypeEntry) symbolTable.get(state.varTypes.get(i), SymbolEntry.SymbolType.TYPE);
                    if (varType == null && !state.varTypes.get(i).equals("int") && !state.varTypes.get(i).equals("float")) {
                        System.out.println("\nSemantic error (line " + token.getLine() + "): \"" + state.varTypes.get(i) + "\" type was not declared");

                        success = false;
                    }
                    SymbolEntry varEntry = new VarEntry(state.varIDs.get(i), state.varTypes.get(i), type);
                    symbolTable.addEntry(varEntry);
                    ((FuncEntry) entry).addArg(state.varIDs.get(i), state.varTypes.get(i));
                }
            }
            if (token.getToken() == TokenEnum.SEMI) {
                stateStack.pop();
                symbolTable.removeLevel();
            }
        }
        if (state.derivation == NTID.VD) {
            if (token.getToken() == TokenEnum.ID) {
                if (state.getType) {
                    state.typeid = token.getLit();
                } else {
                    state.varIDs.add(token.getLit());
                }
            }
            if (token.getToken() == TokenEnum.INT) {
                state.typeid = token.getLit();
            }
            if (token.getToken() == TokenEnum.FLOAT) {
                state.typeid = token.getLit();
            }
            if (token.getToken() == TokenEnum.COLON) {
                state.getType = true;
            }
            if (token.getToken() == TokenEnum.ASSIGN) {
                state.isRight = true;
            }
            if (token.getToken() == TokenEnum.SEMI) {
                TypeEntry type = (TypeEntry) symbolTable.get(state.typeid, SymbolEntry.SymbolType.TYPE);
                if (type == null && !state.typeid.equals("int") && !state.typeid.equals("float")) {
                    System.out.println("\nSemantic error (line " + token.getLine() + "): \"" + state.typeid + "\" type was not declared");

                    success = false;
                }
                for (String s : state.varIDs) {
                    VarEntry newEntry = new VarEntry(s, state.typeid, type);
                    if (state.isRight) {
                        newEntry.isInitialized = true;
                    }
                    symbolTable.addEntry(newEntry);
                }
                stateStack.pop();
            }
        }
        if (state.derivation == NTID.S) {
            if (token.getToken() == TokenEnum.SEMI) {
                if (state.isRight && state.lvalue != null) {
                    VarEntry var = (VarEntry) symbolTable.get(state.lvalue, SymbolEntry.SymbolType.VAR);
                    if (var != null) {
                        var.isInitialized = true;
                    }
                }
                stateStack.pop();
            }
            if (token.getToken() == TokenEnum.ID) {
                VarEntry var = (VarEntry) symbolTable.get(token.getLit(), SymbolEntry.SymbolType.VAR);
                FuncEntry func = (FuncEntry) symbolTable.get(token.getLit(), SymbolEntry.SymbolType.FUNC);
                if (var == null && func == null && !state.getRecordVal) {
                    System.out.println("\nSemantic error (line " + token.getLine() + "): \"" + token.getLit() + "\" was not declared");

                    success = false;
                } else {
                    if (state.isRight && !state.getRecordVal && !var.isInitialized) {
                        System.out.println("\nSemantic error (line " + token.getLine() + "): \"" + token.getLit() + "\" variable was not initialized");

                        success = false;

                    }
                    if (!state.isRight) {
                        if (state.getRecordVal) {
                            state.recordLvalue2 = token.getLit();
                        } else {
                            state.lvalue = token.getLit();
                        }
                    }
                }
                if (state.getRecordVal) {
                    state.getRecordVal = false;
                }
            }
            if (token.getToken() == TokenEnum.ASSIGN) {
                state.isRight = true;
            }
            if (token.getToken() == TokenEnum.PERIOD) {
                state.getRecordVal = true;
            }
        }
        if (state.derivation == NTID.E1p || state.derivation == NTID.E2p || state.derivation == NTID.E3p || state.derivation == NTID.E4p || state.derivation == NTID.E5p) {
            //System.out.println("DERIVATION IS "  + state.derivation);
            stateStack.pop();
        }

        state.entry = entry;
        return true;
    }

    private boolean nonTerminalSemantics(DerivationItem item, Stack<DerivationState> stateStack, int derivationNumber) {

        // SEMANTIC LOGIC

        DerivationState state = stateStack.peek();

        if (item.nonTerminal == NTID.S && derivationNumber == 6) {
            stateStack.push(new DerivationState(NTID.TP));
            symbolTable.addLevel();
        }
        if (item.nonTerminal == NTID.FD) {
            stateStack.push(new DerivationState(NTID.FD));
            stateStack.peek().varIDs = new ArrayList<>();
            stateStack.peek().varTypes = new ArrayList<>();
        }
        if (item.nonTerminal == NTID.TD) {
            stateStack.push(new DerivationState(NTID.TD));
        }
        if (item.nonTerminal == NTID.VD) {
            stateStack.push(new DerivationState(NTID.VD));
            stateStack.peek().varIDs = new ArrayList<>();
        }
        if (item.nonTerminal == NTID.S) {
            stateStack.push(new DerivationState(NTID.S));
        }
        if (state.derivation == NTID.VD && item.nonTerminal == NTID.T) {
            state.getType = true;
        }
        // make sure to pop
        if (item.nonTerminal == NTID.E1p && derivationNumber == 0) {
            //IR add OR operation
            stateStack.push(new DerivationState(NTID.E1));
        }
        if (item.nonTerminal == NTID.E2p && derivationNumber == 0) {
            //IR add AND operation
            stateStack.push(new DerivationState(NTID.E2p));
        }
        if (item.nonTerminal == NTID.E3p && derivationNumber == 0) {
            // IR add <= operation
            stateStack.push(new DerivationState((NTID.E3p)));
        }
        if (item.nonTerminal == NTID.E3p && derivationNumber == 1) {
            // IR add >= operation

            stateStack.push(new DerivationState((NTID.E3p)));
        }
        if (item.nonTerminal == NTID.E3p && derivationNumber == 2) {
            // IR add < operation

            stateStack.push(new DerivationState((NTID.E3p)));
        }
        if (item.nonTerminal == NTID.E3p && derivationNumber == 3) {
            // IR add > operation

            stateStack.push(new DerivationState((NTID.E3p)));
        }
        if (item.nonTerminal == NTID.E3p && derivationNumber == 4) {
            // IR add <> operation
            stateStack.push(new DerivationState((NTID.E3p)));
        }
        if (item.nonTerminal == NTID.E3p && derivationNumber == 5) {
            // IR add = operation
            stateStack.push(new DerivationState((NTID.E3p)));
        }
        if (item.nonTerminal == NTID.E4p && derivationNumber == 0) {
            // IR add - operation
            stateStack.push(new DerivationState((NTID.E4p)));
        }
        if (item.nonTerminal == NTID.E4p && derivationNumber == 1) {
            // IR add + operation
           // System.out.println("here");

            stateStack.push(new DerivationState(NTID.E4p));
        }
        if (item.nonTerminal == NTID.E5p && derivationNumber == 0) {
            // IR add / operation
            stateStack.push(new DerivationState(NTID.E5p));
        }
        if (item.nonTerminal == NTID.E5p && derivationNumber == 1) {
            // IR add * operation
           // System.out.println("here");

            stateStack.push(new DerivationState(NTID.E5p));
        }

        return true;
    }

    public TigerParser(TigerScanner scanner, SymbolTable table) {
        previousState = NTID.TP;
        stateHistory = new ArrayList<NTID>();
        this.scanner = scanner;
        this.symbolTable = table;
        success = true;
        nonTerminals = new NonTerminal[100];        //TODO: change array size
        parserTable = new int[100][100];            //TODO: change array size

        // illegal values in parse table should be -1
        for (int i = 0; i < parserTable.length; ++i) {
            for (int j = 0; j < parserTable[i].length; ++j) {
                parserTable[i][j] = -1;
            }
        }

        // 0 TP
        ArrayList<DerivationItem>[] TP = new ArrayList[1];
        TP[0] = new ArrayList<>();
        TP[0].add(new DerivationItem(TokenEnum.LET));
        TP[0].add(new DerivationItem(NTID.DS));
        TP[0].add(new DerivationItem(TokenEnum.IN));
        TP[0].add(new DerivationItem(NTID.SS));
        TP[0].add(new DerivationItem(TokenEnum.END));
        nonTerminals[NTID.TP.ordinal()] = new NonTerminal(TP);

        // 1 DS
        ArrayList<DerivationItem>[] DS = new ArrayList[1];
        DS[0] = new ArrayList<>();
        DS[0].add(new DerivationItem(NTID.TDL));
        DS[0].add(new DerivationItem(NTID.VDL));
        DS[0].add(new DerivationItem(NTID.FDL));
        nonTerminals[NTID.DS.ordinal()] = new NonTerminal(DS);

        // 2 TDL
        ArrayList<DerivationItem>[] TDL = new ArrayList[2];
        TDL[0] = new ArrayList<>();      // epsilon
        TDL[1] = new ArrayList<>();
        TDL[1].add(new DerivationItem(NTID.TD));
        TDL[1].add(new DerivationItem(NTID.TDL));
        nonTerminals[NTID.TDL.ordinal()] = new NonTerminal(TDL);

        // 3 VDL
        ArrayList<DerivationItem>[] VDL = new ArrayList[2];
        VDL[0] = new ArrayList<>();      // epsilon
        VDL[1] = new ArrayList<>();
        VDL[1].add(new DerivationItem(NTID.VD));
        VDL[1].add(new DerivationItem(NTID.VDL));
        nonTerminals[NTID.VDL.ordinal()] = new NonTerminal(VDL);

        // 4 FDL
        ArrayList<DerivationItem>[] FDL = new ArrayList[2];
        FDL[0] = new ArrayList<>();      // epsilon
        FDL[1] = new ArrayList<>();
        FDL[1].add(new DerivationItem(NTID.FD));
        FDL[1].add(new DerivationItem(NTID.FDL));
        nonTerminals[NTID.FDL.ordinal()] = new NonTerminal(FDL);

        // 5 TD
        ArrayList<DerivationItem>[] TD = new ArrayList[1];
        TD[0] = new ArrayList<>();
        TD[0].add(new DerivationItem(TokenEnum.TYPE));
        TD[0].add(new DerivationItem(TokenEnum.ID));
        TD[0].add(new DerivationItem(TokenEnum.EQ));
        TD[0].add(new DerivationItem(NTID.T));
        TD[0].add(new DerivationItem(TokenEnum.SEMI));
        nonTerminals[NTID.TD.ordinal()] = new NonTerminal(TD);

        // 6 T
        ArrayList<DerivationItem>[] T = new ArrayList[4];
        T[0] = new ArrayList<>();
        T[0].add(new DerivationItem(NTID.TID));
        T[1] = new ArrayList<>();
        T[1].add(new DerivationItem(TokenEnum.ARRAY));
        T[1].add(new DerivationItem(TokenEnum.LBRACK));
        T[1].add(new DerivationItem(TokenEnum.INTLIT));
        T[1].add(new DerivationItem(TokenEnum.RBRACK));
        T[1].add(new DerivationItem(TokenEnum.OF));
        T[1].add(new DerivationItem(NTID.TID));
        T[2] = new ArrayList<>();
        T[2].add(new DerivationItem(TokenEnum.RECORD));
        T[2].add(new DerivationItem(NTID.FL));
        T[2].add(new DerivationItem(TokenEnum.END));
        T[3] = new ArrayList<>();
        T[3].add(new DerivationItem(TokenEnum.ID));
        nonTerminals[NTID.T.ordinal()] = new NonTerminal(T);

        // 7 FL
        ArrayList<DerivationItem>[] FL = new ArrayList[2];
        FL[0] = new ArrayList<>();
        FL[0].add(new DerivationItem(TokenEnum.ID));
        FL[0].add(new DerivationItem(TokenEnum.COLON));
        FL[0].add(new DerivationItem(NTID.TID));
        FL[0].add(new DerivationItem(TokenEnum.SEMI));
        FL[0].add(new DerivationItem(NTID.FL));
        FL[1] = new ArrayList<>();      // epsilon
        nonTerminals[NTID.FL.ordinal()] = new NonTerminal(FL);

        // 8 TID
        ArrayList<DerivationItem>[] TID = new ArrayList[2];
        TID[0] = new ArrayList<>();
        TID[0].add(new DerivationItem(TokenEnum.INT));
        TID[1] = new ArrayList<>();
        TID[1].add(new DerivationItem(TokenEnum.FLOAT));
        nonTerminals[NTID.TID.ordinal()] = new NonTerminal(TID);

        // 9 VD
        ArrayList<DerivationItem>[] VD = new ArrayList[1];
        VD[0] = new ArrayList<>();
        VD[0].add(new DerivationItem(TokenEnum.VAR));
        VD[0].add(new DerivationItem(NTID.IDL));
        VD[0].add(new DerivationItem(TokenEnum.COLON));
        VD[0].add(new DerivationItem(NTID.T));
        VD[0].add(new DerivationItem(NTID.OI));
        VD[0].add(new DerivationItem(TokenEnum.SEMI));
        nonTerminals[NTID.VD.ordinal()] = new NonTerminal(VD);

        // 10 IDL
        ArrayList<DerivationItem>[] IDL = new ArrayList[1];
        IDL[0] = new ArrayList<>();
        IDL[0].add(new DerivationItem(TokenEnum.ID));
        IDL[0].add(new DerivationItem(NTID.IDLp));
        nonTerminals[NTID.IDL.ordinal()] = new NonTerminal(IDL);

        // 11 IDLp
        ArrayList<DerivationItem>[] IDLp = new ArrayList[2];
        IDLp[0] = new ArrayList<>();      // epsilon
        IDLp[1] = new ArrayList<>();
        IDLp[1].add(new DerivationItem(TokenEnum.COMMA));
        IDLp[1].add(new DerivationItem(NTID.IDL));
        nonTerminals[NTID.IDLp.ordinal()] = new NonTerminal(IDLp);

        // 12 OI
        ArrayList<DerivationItem>[] OI = new ArrayList[2];
        OI[0] = new ArrayList<>();      // epsilon
        OI[1] = new ArrayList<>();
        OI[1].add(new DerivationItem(TokenEnum.ASSIGN));
        OI[1].add(new DerivationItem(NTID.C));
        nonTerminals[NTID.OI.ordinal()] = new NonTerminal(OI);

        // 13 FD
        ArrayList<DerivationItem>[] FD = new ArrayList[1];
        FD[0] = new ArrayList<>();
        FD[0].add(new DerivationItem(TokenEnum.FUNC));
        FD[0].add(new DerivationItem(TokenEnum.ID));
        FD[0].add(new DerivationItem(TokenEnum.LPAREN));
        FD[0].add(new DerivationItem(NTID.PL));
        FD[0].add(new DerivationItem(TokenEnum.RPAREN));
        FD[0].add(new DerivationItem(NTID.RT));
        FD[0].add(new DerivationItem(TokenEnum.BEGIN));
        FD[0].add(new DerivationItem(NTID.SS));
        FD[0].add(new DerivationItem(TokenEnum.END));
        FD[0].add(new DerivationItem(TokenEnum.SEMI));
        nonTerminals[NTID.FD.ordinal()] = new NonTerminal(FD);

        // 14 PL
        ArrayList<DerivationItem>[] PL = new ArrayList[2];
        PL[0] = new ArrayList<>();      // epsilon
        PL[1] = new ArrayList<>();
        PL[1].add(new DerivationItem(NTID.P));
        PL[1].add(new DerivationItem(NTID.PLT));
        nonTerminals[NTID.PL.ordinal()] = new NonTerminal(PL);

        // 15 PLT
        ArrayList<DerivationItem>[] PLT = new ArrayList[2];
        PLT[0] = new ArrayList<>();      // epsilon
        PLT[1] = new ArrayList<>();
        PLT[1].add(new DerivationItem(TokenEnum.COMMA));
        PLT[1].add(new DerivationItem(NTID.P));
        PLT[1].add(new DerivationItem(NTID.PLT));
        nonTerminals[NTID.PLT.ordinal()] = new NonTerminal(PLT);

        // 16 RT
        ArrayList<DerivationItem>[] RT = new ArrayList[2];
        RT[0] = new ArrayList<>();      // epsilon
        RT[1] = new ArrayList<>();
        RT[1].add(new DerivationItem(TokenEnum.COLON));
        RT[1].add(new DerivationItem(NTID.T));
        nonTerminals[NTID.RT.ordinal()] = new NonTerminal(RT);

        // 17 P
        ArrayList<DerivationItem>[] P = new ArrayList[1];
        P[0] = new ArrayList<>();
        P[0].add(new DerivationItem(TokenEnum.ID));
        P[0].add(new DerivationItem(TokenEnum.COLON));
        P[0].add(new DerivationItem(NTID.T));
        nonTerminals[NTID.P.ordinal()] = new NonTerminal(P);

        // 18 SS
        ArrayList<DerivationItem>[] SS = new ArrayList[1];
        SS[0] = new ArrayList<>();
        SS[0].add(new DerivationItem(NTID.S));
        SS[0].add(new DerivationItem(NTID.SSp));
        nonTerminals[NTID.SS.ordinal()] = new NonTerminal(SS);

        // 19 SSp
        ArrayList<DerivationItem>[] SSp = new ArrayList[2];
        SSp[0] = new ArrayList<>();      // epsilon
        SSp[1] = new ArrayList<>();
        SSp[1].add(new DerivationItem(NTID.SS));
        nonTerminals[NTID.SSp.ordinal()] = new NonTerminal(SSp);

        // 20 S
        ArrayList<DerivationItem>[] S = new ArrayList[7];
        S[0] = new ArrayList<>();
        S[0].add(new DerivationItem(TokenEnum.IF));
        S[0].add(new DerivationItem(NTID.E));
        S[0].add(new DerivationItem(TokenEnum.THEN));
        S[0].add(new DerivationItem(NTID.SS));
        S[0].add(new DerivationItem(NTID.Sp));
        S[0].add(new DerivationItem(TokenEnum.SEMI));
        S[1] = new ArrayList<>();
        S[1].add(new DerivationItem(TokenEnum.WHILE));
        S[1].add(new DerivationItem(NTID.E));
        S[1].add(new DerivationItem(TokenEnum.DO));
        S[1].add(new DerivationItem(NTID.SS));
        S[1].add(new DerivationItem(TokenEnum.ENDDO));
        S[1].add(new DerivationItem(TokenEnum.SEMI));
        S[2] = new ArrayList<>();
        S[2].add(new DerivationItem(TokenEnum.FOR));
        S[2].add(new DerivationItem(TokenEnum.ID));
        S[2].add(new DerivationItem(TokenEnum.ASSIGN));
        S[2].add(new DerivationItem(NTID.E));
        S[2].add(new DerivationItem(TokenEnum.TO));
        S[2].add(new DerivationItem(NTID.E));
        S[2].add(new DerivationItem(TokenEnum.DO));
        S[2].add(new DerivationItem(NTID.SS));
        S[2].add(new DerivationItem(TokenEnum.ENDDO));
        S[2].add(new DerivationItem(TokenEnum.SEMI));
        S[3] = new ArrayList<>();
        S[3].add(new DerivationItem(TokenEnum.ID));
        S[3].add(new DerivationItem(NTID.Spp));
        S[3].add(new DerivationItem(TokenEnum.SEMI));
        S[4] = new ArrayList<>();
        S[4].add(new DerivationItem(TokenEnum.BREAK));
        S[4].add(new DerivationItem(TokenEnum.SEMI));
        S[5] = new ArrayList<>();
        S[5].add(new DerivationItem(TokenEnum.RETURN));
        S[5].add(new DerivationItem(NTID.E));
        S[5].add(new DerivationItem(TokenEnum.SEMI));
        S[6] = new ArrayList<>();
        S[6].add(new DerivationItem(TokenEnum.LET));
        S[6].add(new DerivationItem(NTID.DS));
        S[6].add(new DerivationItem(TokenEnum.IN));
        S[6].add(new DerivationItem(NTID.SS));
        S[6].add(new DerivationItem(TokenEnum.END));
        nonTerminals[NTID.S.ordinal()] = new NonTerminal(S);

        // 21 Sp
        ArrayList<DerivationItem>[] Sp = new ArrayList[2];
        Sp[0] = new ArrayList<>();
        Sp[0].add(new DerivationItem(TokenEnum.ENDIF));
        Sp[1] = new ArrayList<>();
        Sp[1].add(new DerivationItem(TokenEnum.ELSE));
        Sp[1].add(new DerivationItem(NTID.SS));
        Sp[1].add(new DerivationItem(TokenEnum.ENDIF));
        nonTerminals[NTID.Sp.ordinal()] = new NonTerminal(Sp);

        // 22 Spp
        ArrayList<DerivationItem>[] Spp = new ArrayList[2];
        Spp[0] = new ArrayList<>();
        Spp[0].add(new DerivationItem(TokenEnum.LPAREN));
        Spp[0].add(new DerivationItem(NTID.EL));
        Spp[0].add(new DerivationItem(TokenEnum.RPAREN));
        Spp[1] = new ArrayList<>();
        Spp[1].add(new DerivationItem(NTID.LVT));
        Spp[1].add(new DerivationItem(TokenEnum.ASSIGN));
        Spp[1].add(new DerivationItem(NTID.Sppp));
        nonTerminals[NTID.Spp.ordinal()] = new NonTerminal(Spp);

        // 23 Sppp
        ArrayList<DerivationItem>[] Sppp = new ArrayList[3];
        Sppp[0] = new ArrayList<>();
        Sppp[0].add(new DerivationItem(NTID.C));
        Sppp[0].add(new DerivationItem(NTID.E5p));
        Sppp[0].add(new DerivationItem(NTID.E4p));
        Sppp[0].add(new DerivationItem(NTID.E3p));
        Sppp[0].add(new DerivationItem(NTID.E2p));
        Sppp[0].add(new DerivationItem(NTID.E1p));
        Sppp[1] = new ArrayList<>();
        Sppp[1].add(new DerivationItem(TokenEnum.LPAREN));
        Sppp[1].add(new DerivationItem(NTID.E));
        Sppp[1].add(new DerivationItem(TokenEnum.RPAREN));
        Sppp[1].add(new DerivationItem(NTID.E5p));
        Sppp[1].add(new DerivationItem(NTID.E4p));
        Sppp[1].add(new DerivationItem(NTID.E3p));
        Sppp[1].add(new DerivationItem(NTID.E2p));
        Sppp[1].add(new DerivationItem(NTID.E1p));
        Sppp[2] = new ArrayList<>();
        Sppp[2].add(new DerivationItem(TokenEnum.ID));
        Sppp[2].add(new DerivationItem(NTID.Spppp));
        nonTerminals[NTID.Sppp.ordinal()] = new NonTerminal(Sppp);

        // 24 Spppp
        ArrayList<DerivationItem>[] Spppp = new ArrayList[2];
        Spppp[0] = new ArrayList<>();
        Spppp[0].add(new DerivationItem(TokenEnum.LPAREN));
        Spppp[0].add(new DerivationItem(NTID.EL));
        Spppp[0].add(new DerivationItem(TokenEnum.RPAREN));
        Spppp[1] = new ArrayList<>();
        Spppp[1].add(new DerivationItem(NTID.LVT));
        Spppp[1].add(new DerivationItem(NTID.E5p));
        Spppp[1].add(new DerivationItem(NTID.E4p));
        Spppp[1].add(new DerivationItem(NTID.E3p));
        Spppp[1].add(new DerivationItem(NTID.E2p));
        Spppp[1].add(new DerivationItem(NTID.E1p));
        nonTerminals[NTID.Spppp.ordinal()] = new NonTerminal(Spppp);

        // 25 E
        ArrayList<DerivationItem>[] E = new ArrayList[1];
        E[0] = new ArrayList<>();
        E[0].add(new DerivationItem(NTID.E1));
        nonTerminals[NTID.E.ordinal()] = new NonTerminal(E);

        // 26 E1
        ArrayList<DerivationItem>[] E1 = new ArrayList[1];
        E1[0] = new ArrayList<>();
        E1[0].add(new DerivationItem(NTID.E2));
        E1[0].add(new DerivationItem(NTID.E1p));
        nonTerminals[NTID.E1.ordinal()] = new NonTerminal(E1);

        // 27 E1p
        ArrayList<DerivationItem>[] E1p = new ArrayList[2];
        E1p[0] = new ArrayList<>();
        E1p[0].add(new DerivationItem(TokenEnum.OR));
        E1p[0].add(new DerivationItem(NTID.E2));
        E1p[0].add(new DerivationItem(NTID.E1p));
        E1p[1] = new ArrayList<>();      // epsilon
        nonTerminals[NTID.E1p.ordinal()] = new NonTerminal(E1p);

        // 28 E2
        ArrayList<DerivationItem>[] E2 = new ArrayList[1];
        E2[0] = new ArrayList<>();
        E2[0].add(new DerivationItem(NTID.E3));
        E2[0].add(new DerivationItem(NTID.E2p));
        nonTerminals[NTID.E2.ordinal()] = new NonTerminal(E2);

        // 29 E2p
        ArrayList<DerivationItem>[] E2p = new ArrayList[2];
        E2p[0] = new ArrayList<>();
        E2p[0].add(new DerivationItem(TokenEnum.AND));
        E2p[0].add(new DerivationItem(NTID.E3));
        E2p[0].add(new DerivationItem(NTID.E2p));
        E2p[1] = new ArrayList<>();      // epsilon
        nonTerminals[NTID.E2p.ordinal()] = new NonTerminal(E2p);

        // 30 E3
        ArrayList<DerivationItem>[] E3 = new ArrayList[1];
        E3[0] = new ArrayList<>();
        E3[0].add(new DerivationItem(NTID.E4));
        E3[0].add(new DerivationItem(NTID.E3p));
        nonTerminals[NTID.E3.ordinal()] = new NonTerminal(E3);

        // 31 E3p
        ArrayList<DerivationItem>[] E3p = new ArrayList[7];
        E3p[0] = new ArrayList<>();
        E3p[0].add(new DerivationItem(TokenEnum.LESSEREQ));
        E3p[0].add(new DerivationItem(NTID.E4));
        E3p[0].add(new DerivationItem(NTID.E3p));
        E3p[1] = new ArrayList<>();
        E3p[1].add(new DerivationItem(TokenEnum.GREATEREQ));
        E3p[1].add(new DerivationItem(NTID.E4));
        E3p[1].add(new DerivationItem(NTID.E3p));
        E3p[2] = new ArrayList<>();
        E3p[2].add(new DerivationItem(TokenEnum.LESSER));
        E3p[2].add(new DerivationItem(NTID.E4));
        E3p[2].add(new DerivationItem(NTID.E3p));
        E3p[3] = new ArrayList<>();
        E3p[3].add(new DerivationItem(TokenEnum.GREATER));
        E3p[3].add(new DerivationItem(NTID.E4));
        E3p[3].add(new DerivationItem(NTID.E3p));
        E3p[4] = new ArrayList<>();
        E3p[4].add(new DerivationItem(TokenEnum.NEQ));
        E3p[4].add(new DerivationItem(NTID.E4));
        E3p[4].add(new DerivationItem(NTID.E3p));
        E3p[5] = new ArrayList<>();
        E3p[5].add(new DerivationItem(TokenEnum.EQ));
        E3p[5].add(new DerivationItem(NTID.E4));
        E3p[5].add(new DerivationItem(NTID.E3p));
        E3p[6] = new ArrayList<>();      // epsilon
        nonTerminals[NTID.E3p.ordinal()] = new NonTerminal(E3p);

        // 32 E4
        ArrayList<DerivationItem>[] E4 = new ArrayList[1];
        E4[0] = new ArrayList<>();
        E4[0].add(new DerivationItem(NTID.E5));
        E4[0].add(new DerivationItem(NTID.E4p));
        nonTerminals[NTID.E4.ordinal()] = new NonTerminal(E4);

        // 33 E4p
        ArrayList<DerivationItem>[] E4p = new ArrayList[3];
        E4p[0] = new ArrayList<>();
        E4p[0].add(new DerivationItem(TokenEnum.MINUS));
        E4p[0].add(new DerivationItem(NTID.E5));
        E4p[0].add(new DerivationItem(NTID.E4p));
        E4p[1] = new ArrayList<>();
        E4p[1].add(new DerivationItem(TokenEnum.PLUS));
        E4p[1].add(new DerivationItem(NTID.E5));
        E4p[1].add(new DerivationItem(NTID.E4p));
        E4p[2] = new ArrayList<>();      // epsilon
        nonTerminals[NTID.E4p.ordinal()] = new NonTerminal(E4p);

        // 34 E5
        ArrayList<DerivationItem>[] E5 = new ArrayList[1];
        E5[0] = new ArrayList<>();
        E5[0].add(new DerivationItem(NTID.E6));
        E5[0].add(new DerivationItem(NTID.E5p));
        nonTerminals[NTID.E5.ordinal()] = new NonTerminal(E5);

        // 35 E5p
        ArrayList<DerivationItem>[] E5p = new ArrayList[3];
        E5p[0] = new ArrayList<>();
        E5p[0].add(new DerivationItem(TokenEnum.DIV));
        E5p[0].add(new DerivationItem(NTID.E6));
        E5p[0].add(new DerivationItem(NTID.E5p));
        E5p[1] = new ArrayList<>();
        E5p[1].add(new DerivationItem(TokenEnum.MULT));
        E5p[1].add(new DerivationItem(NTID.E6));
        E5p[1].add(new DerivationItem(NTID.E5p));
        E5p[2] = new ArrayList<>();      // epsilon
        nonTerminals[NTID.E5p.ordinal()] = new NonTerminal(E5p);

        // 36 E6
        ArrayList<DerivationItem>[] E6 = new ArrayList[3];
        E6[0] = new ArrayList<>();
        E6[0].add(new DerivationItem(TokenEnum.LPAREN));
        E6[0].add(new DerivationItem(NTID.E));
        E6[0].add(new DerivationItem(TokenEnum.RPAREN));
        E6[1] = new ArrayList<>();
        E6[1].add(new DerivationItem(NTID.C));
        E6[2] = new ArrayList<>();
        E6[2].add(new DerivationItem(NTID.LV));
        nonTerminals[NTID.E6.ordinal()] = new NonTerminal(E6);

        // 37 C
        ArrayList<DerivationItem>[] C = new ArrayList[2];
        C[0] = new ArrayList<>();
        C[0].add(new DerivationItem(TokenEnum.INTLIT));
        C[1] = new ArrayList<>();
        C[1].add(new DerivationItem(TokenEnum.FLOATLIT));
        nonTerminals[NTID.C.ordinal()] = new NonTerminal(C);

        // 38 EL
        ArrayList<DerivationItem>[] EL = new ArrayList[2];
        EL[0] = new ArrayList<>();      // epsilon
        EL[1] = new ArrayList<>();
        EL[1].add(new DerivationItem(NTID.E));
        EL[1].add(new DerivationItem(NTID.ELT));
        nonTerminals[NTID.EL.ordinal()] = new NonTerminal(EL);

        // 39 ELT
        ArrayList<DerivationItem>[] ELT = new ArrayList[2];
        ELT[0] = new ArrayList<>();
        ELT[0].add(new DerivationItem(TokenEnum.COMMA));
        ELT[0].add(new DerivationItem(NTID.E));
        ELT[0].add(new DerivationItem(NTID.ELT));
        ELT[1] = new ArrayList<>();      // epsilon
        nonTerminals[NTID.ELT.ordinal()] = new NonTerminal(ELT);

        // 40 LV
        ArrayList<DerivationItem>[] LV = new ArrayList[1];
        LV[0] = new ArrayList<>();
        LV[0].add(new DerivationItem(TokenEnum.ID));
        LV[0].add(new DerivationItem(NTID.LVT));
        nonTerminals[NTID.LV.ordinal()] = new NonTerminal(LV);

        // 41 LVT
        ArrayList<DerivationItem>[] LVT = new ArrayList[3];
        LVT[0] = new ArrayList<>();
        LVT[0].add(new DerivationItem(TokenEnum.LBRACK));
        LVT[0].add(new DerivationItem(NTID.E));
        LVT[0].add(new DerivationItem(TokenEnum.RBRACK));
        LVT[1] = new ArrayList<>();
        LVT[1].add(new DerivationItem(TokenEnum.PERIOD));
        LVT[1].add(new DerivationItem(TokenEnum.ID));
        LVT[2] = new ArrayList<>();      // epsilon
        nonTerminals[NTID.LVT.ordinal()] = new NonTerminal(LVT);


        // Parse Table

        parserTable[NTID.TP.ordinal()][TokenEnum.LET.ordinal()] = 0;

        parserTable[NTID.DS.ordinal()][TokenEnum.IN.ordinal()] = 0;
        parserTable[NTID.DS.ordinal()][TokenEnum.TYPE.ordinal()] = 0;
        parserTable[NTID.DS.ordinal()][TokenEnum.VAR.ordinal()] = 0;
        parserTable[NTID.DS.ordinal()][TokenEnum.FUNC.ordinal()] = 0;

        parserTable[NTID.TDL.ordinal()][TokenEnum.IN.ordinal()] = 0;
        parserTable[NTID.TDL.ordinal()][TokenEnum.TYPE.ordinal()] = 1;
        parserTable[NTID.TDL.ordinal()][TokenEnum.VAR.ordinal()] = 0;
        parserTable[NTID.TDL.ordinal()][TokenEnum.FUNC.ordinal()] = 0;

        parserTable[NTID.VDL.ordinal()][TokenEnum.IN.ordinal()] = 0;
        parserTable[NTID.VDL.ordinal()][TokenEnum.VAR.ordinal()] = 1;
        parserTable[NTID.VDL.ordinal()][TokenEnum.FUNC.ordinal()] = 0;

        parserTable[NTID.FDL.ordinal()][TokenEnum.IN.ordinal()] = 0;
        parserTable[NTID.FDL.ordinal()][TokenEnum.FUNC.ordinal()] = 1;

        parserTable[NTID.TD.ordinal()][TokenEnum.TYPE.ordinal()] = 0;

        parserTable[NTID.T.ordinal()][TokenEnum.ID.ordinal()] = 3;
        parserTable[NTID.T.ordinal()][TokenEnum.ARRAY.ordinal()] = 1;
        parserTable[NTID.T.ordinal()][TokenEnum.RECORD.ordinal()] = 2;
        parserTable[NTID.T.ordinal()][TokenEnum.INT.ordinal()] = 0;
        parserTable[NTID.T.ordinal()][TokenEnum.FLOAT.ordinal()] = 0;

        parserTable[NTID.FL.ordinal()][TokenEnum.END.ordinal()] = 1;
        parserTable[NTID.FL.ordinal()][TokenEnum.ID.ordinal()] = 0;

        parserTable[NTID.TID.ordinal()][TokenEnum.INT.ordinal()] = 0;
        parserTable[NTID.TID.ordinal()][TokenEnum.FLOAT.ordinal()] = 1;

        parserTable[NTID.VD.ordinal()][TokenEnum.VAR.ordinal()] = 0;

        parserTable[NTID.IDL.ordinal()][TokenEnum.ID.ordinal()] = 0;

        parserTable[NTID.IDLp.ordinal()][TokenEnum.COLON.ordinal()] = 0;
        parserTable[NTID.IDLp.ordinal()][TokenEnum.COMMA.ordinal()] = 1;

        parserTable[NTID.OI.ordinal()][TokenEnum.SEMI.ordinal()] = 0;
        parserTable[NTID.OI.ordinal()][TokenEnum.ASSIGN.ordinal()] = 1;

        parserTable[NTID.FD.ordinal()][TokenEnum.FUNC.ordinal()] = 0;

        parserTable[NTID.PL.ordinal()][TokenEnum.ID.ordinal()] = 1;
        parserTable[NTID.PL.ordinal()][TokenEnum.RPAREN.ordinal()] = 0;

        parserTable[NTID.PLT.ordinal()][TokenEnum.COMMA.ordinal()] = 1;
        parserTable[NTID.PLT.ordinal()][TokenEnum.RPAREN.ordinal()] = 0;

        parserTable[NTID.RT.ordinal()][TokenEnum.COLON.ordinal()] = 1;
        parserTable[NTID.RT.ordinal()][TokenEnum.BEGIN.ordinal()] = 0;

        parserTable[NTID.P.ordinal()][TokenEnum.ID.ordinal()] = 0;

        parserTable[NTID.SS.ordinal()][TokenEnum.LET.ordinal()] = 0;
        parserTable[NTID.SS.ordinal()][TokenEnum.ID.ordinal()] = 0;
        parserTable[NTID.SS.ordinal()][TokenEnum.IF.ordinal()] = 0;
        parserTable[NTID.SS.ordinal()][TokenEnum.WHILE.ordinal()] = 0;
        parserTable[NTID.SS.ordinal()][TokenEnum.FOR.ordinal()] = 0;
        parserTable[NTID.SS.ordinal()][TokenEnum.BREAK.ordinal()] = 0;
        parserTable[NTID.SS.ordinal()][TokenEnum.RETURN.ordinal()] = 0;

        parserTable[NTID.SSp.ordinal()][TokenEnum.LET.ordinal()] = 1;
        parserTable[NTID.SSp.ordinal()][TokenEnum.END.ordinal()] = 0;
        parserTable[NTID.SSp.ordinal()][TokenEnum.ID.ordinal()] = 1;
        parserTable[NTID.SSp.ordinal()][TokenEnum.IF.ordinal()] = 1;
        parserTable[NTID.SSp.ordinal()][TokenEnum.ENDIF.ordinal()] = 0;
        parserTable[NTID.SSp.ordinal()][TokenEnum.ELSE.ordinal()] = 0;
        parserTable[NTID.SSp.ordinal()][TokenEnum.WHILE.ordinal()] = 1;
        parserTable[NTID.SSp.ordinal()][TokenEnum.ENDDO.ordinal()] = 0;
        parserTable[NTID.SSp.ordinal()][TokenEnum.FOR.ordinal()] = 1;
        parserTable[NTID.SSp.ordinal()][TokenEnum.BREAK.ordinal()] = 1;
        parserTable[NTID.SSp.ordinal()][TokenEnum.RETURN.ordinal()] = 1;

        parserTable[NTID.S.ordinal()][TokenEnum.LET.ordinal()] = 6;
        parserTable[NTID.S.ordinal()][TokenEnum.ID.ordinal()] = 3;
        parserTable[NTID.S.ordinal()][TokenEnum.IF.ordinal()] = 0;
        parserTable[NTID.S.ordinal()][TokenEnum.WHILE.ordinal()] = 1;
        parserTable[NTID.S.ordinal()][TokenEnum.FOR.ordinal()] = 2;
        parserTable[NTID.S.ordinal()][TokenEnum.BREAK.ordinal()] = 4;
        parserTable[NTID.S.ordinal()][TokenEnum.RETURN.ordinal()] = 5;

        parserTable[NTID.Sp.ordinal()][TokenEnum.ENDIF.ordinal()] = 0;
        parserTable[NTID.Sp.ordinal()][TokenEnum.ELSE.ordinal()] = 1;

        parserTable[NTID.Spp.ordinal()][TokenEnum.SEMI.ordinal()] = 1;
        parserTable[NTID.Spp.ordinal()][TokenEnum.LBRACK.ordinal()] = 1;
        parserTable[NTID.Spp.ordinal()][TokenEnum.ASSIGN.ordinal()] = 1;
        parserTable[NTID.Spp.ordinal()][TokenEnum.LPAREN.ordinal()] = 0;
        parserTable[NTID.Spp.ordinal()][TokenEnum.PERIOD.ordinal()] = 1;

        parserTable[NTID.Sppp.ordinal()][TokenEnum.ID.ordinal()] = 2;
        parserTable[NTID.Sppp.ordinal()][TokenEnum.INTLIT.ordinal()] = 0;
        parserTable[NTID.Sppp.ordinal()][TokenEnum.LPAREN.ordinal()] = 1;
        parserTable[NTID.Sppp.ordinal()][TokenEnum.FLOATLIT.ordinal()] = 0;

        parserTable[NTID.Spppp.ordinal()][TokenEnum.EQ.ordinal()] = 1;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.SEMI.ordinal()] = 1;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.LBRACK.ordinal()] = 1;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.LPAREN.ordinal()] = 0;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.OR.ordinal()] = 1;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.AND.ordinal()] = 1;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.LESSEREQ.ordinal()] = 1;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.GREATEREQ.ordinal()] = 1;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.LESSER.ordinal()] = 1;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.GREATER.ordinal()] = 1;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.NEQ.ordinal()] = 1;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.MINUS.ordinal()] = 1;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.PLUS.ordinal()] = 1;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.DIV.ordinal()] = 1;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.MULT.ordinal()] = 1;
        parserTable[NTID.Spppp.ordinal()][TokenEnum.PERIOD.ordinal()] = 1;

        parserTable[NTID.E.ordinal()][TokenEnum.ID.ordinal()] = 0;
        parserTable[NTID.E.ordinal()][TokenEnum.INTLIT.ordinal()] = 0;
        parserTable[NTID.E.ordinal()][TokenEnum.LPAREN.ordinal()] = 0;
        parserTable[NTID.E.ordinal()][TokenEnum.FLOATLIT.ordinal()] = 0;

        parserTable[NTID.E1.ordinal()][TokenEnum.ID.ordinal()] = 0;
        parserTable[NTID.E1.ordinal()][TokenEnum.INTLIT.ordinal()] = 0;
        parserTable[NTID.E1.ordinal()][TokenEnum.LPAREN.ordinal()] = 0;
        parserTable[NTID.E1.ordinal()][TokenEnum.FLOATLIT.ordinal()] = 0;

        parserTable[NTID.E1p.ordinal()][TokenEnum.SEMI.ordinal()] = 1;
        parserTable[NTID.E1p.ordinal()][TokenEnum.RBRACK.ordinal()] = 1;
        parserTable[NTID.E1p.ordinal()][TokenEnum.COMMA.ordinal()] = 1;
        parserTable[NTID.E1p.ordinal()][TokenEnum.RPAREN.ordinal()] = 1;
        parserTable[NTID.E1p.ordinal()][TokenEnum.THEN.ordinal()] = 1;
        parserTable[NTID.E1p.ordinal()][TokenEnum.DO.ordinal()] = 1;
        parserTable[NTID.E1p.ordinal()][TokenEnum.TO.ordinal()] = 1;
        parserTable[NTID.E1p.ordinal()][TokenEnum.OR.ordinal()] = 0;

        parserTable[NTID.E2.ordinal()][TokenEnum.ID.ordinal()] = 0;
        parserTable[NTID.E2.ordinal()][TokenEnum.INTLIT.ordinal()] = 0;
        parserTable[NTID.E2.ordinal()][TokenEnum.LPAREN.ordinal()] = 0;
        parserTable[NTID.E2.ordinal()][TokenEnum.FLOATLIT.ordinal()] = 0;

        parserTable[NTID.E2p.ordinal()][TokenEnum.SEMI.ordinal()] = 1;
        parserTable[NTID.E2p.ordinal()][TokenEnum.RBRACK.ordinal()] = 1;
        parserTable[NTID.E2p.ordinal()][TokenEnum.COMMA.ordinal()] = 1;
        parserTable[NTID.E2p.ordinal()][TokenEnum.RPAREN.ordinal()] = 1;
        parserTable[NTID.E2p.ordinal()][TokenEnum.THEN.ordinal()] = 1;
        parserTable[NTID.E2p.ordinal()][TokenEnum.DO.ordinal()] = 1;
        parserTable[NTID.E2p.ordinal()][TokenEnum.TO.ordinal()] = 1;
        parserTable[NTID.E2p.ordinal()][TokenEnum.OR.ordinal()] = 1;
        parserTable[NTID.E2p.ordinal()][TokenEnum.AND.ordinal()] = 0;

        parserTable[NTID.E3.ordinal()][TokenEnum.ID.ordinal()] = 0;
        parserTable[NTID.E3.ordinal()][TokenEnum.INTLIT.ordinal()] = 0;
        parserTable[NTID.E3.ordinal()][TokenEnum.LPAREN.ordinal()] = 0;
        parserTable[NTID.E3.ordinal()][TokenEnum.FLOATLIT.ordinal()] = 0;

        parserTable[NTID.E3p.ordinal()][TokenEnum.EQ.ordinal()] = 5;
        parserTable[NTID.E3p.ordinal()][TokenEnum.SEMI.ordinal()] = 6;
        parserTable[NTID.E3p.ordinal()][TokenEnum.RBRACK.ordinal()] = 6;
        parserTable[NTID.E3p.ordinal()][TokenEnum.COMMA.ordinal()] = 6;
        parserTable[NTID.E3p.ordinal()][TokenEnum.RPAREN.ordinal()] = 6;
        parserTable[NTID.E3p.ordinal()][TokenEnum.THEN.ordinal()] = 6;
        parserTable[NTID.E3p.ordinal()][TokenEnum.DO.ordinal()] = 6;
        parserTable[NTID.E3p.ordinal()][TokenEnum.TO.ordinal()] = 6;
        parserTable[NTID.E3p.ordinal()][TokenEnum.OR.ordinal()] = 6;
        parserTable[NTID.E3p.ordinal()][TokenEnum.AND.ordinal()] = 6;
        parserTable[NTID.E3p.ordinal()][TokenEnum.LESSEREQ.ordinal()] = 0;
        parserTable[NTID.E3p.ordinal()][TokenEnum.GREATEREQ.ordinal()] = 1;
        parserTable[NTID.E3p.ordinal()][TokenEnum.LESSER.ordinal()] = 2;
        parserTable[NTID.E3p.ordinal()][TokenEnum.GREATER.ordinal()] = 3;
        parserTable[NTID.E3p.ordinal()][TokenEnum.NEQ.ordinal()] = 4;

        parserTable[NTID.E4.ordinal()][TokenEnum.ID.ordinal()] = 0;
        parserTable[NTID.E4.ordinal()][TokenEnum.INTLIT.ordinal()] = 0;
        parserTable[NTID.E4.ordinal()][TokenEnum.LPAREN.ordinal()] = 0;
        parserTable[NTID.E4.ordinal()][TokenEnum.FLOATLIT.ordinal()] = 0;

        parserTable[NTID.E4p.ordinal()][TokenEnum.EQ.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.SEMI.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.RBRACK.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.COMMA.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.RPAREN.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.THEN.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.DO.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.TO.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.OR.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.AND.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.LESSEREQ.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.GREATEREQ.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.LESSER.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.GREATER.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.NEQ.ordinal()] = 2;
        parserTable[NTID.E4p.ordinal()][TokenEnum.MINUS.ordinal()] = 0;
        parserTable[NTID.E4p.ordinal()][TokenEnum.PLUS.ordinal()] = 1;

        parserTable[NTID.E5.ordinal()][TokenEnum.ID.ordinal()] = 0;
        parserTable[NTID.E5.ordinal()][TokenEnum.INTLIT.ordinal()] = 0;
        parserTable[NTID.E5.ordinal()][TokenEnum.LPAREN.ordinal()] = 0;
        parserTable[NTID.E5.ordinal()][TokenEnum.FLOATLIT.ordinal()] = 0;

        parserTable[NTID.E5p.ordinal()][TokenEnum.EQ.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.SEMI.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.RBRACK.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.COMMA.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.RPAREN.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.THEN.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.DO.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.TO.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.OR.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.AND.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.LESSEREQ.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.GREATEREQ.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.LESSER.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.GREATER.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.NEQ.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.MINUS.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.PLUS.ordinal()] = 2;
        parserTable[NTID.E5p.ordinal()][TokenEnum.DIV.ordinal()] = 0;
        parserTable[NTID.E5p.ordinal()][TokenEnum.MULT.ordinal()] = 1;

        parserTable[NTID.E6.ordinal()][TokenEnum.ID.ordinal()] = 2;
        parserTable[NTID.E6.ordinal()][TokenEnum.INTLIT.ordinal()] = 1;
        parserTable[NTID.E6.ordinal()][TokenEnum.LPAREN.ordinal()] = 0;
        parserTable[NTID.E6.ordinal()][TokenEnum.FLOATLIT.ordinal()] = 1;

        parserTable[NTID.C.ordinal()][TokenEnum.INTLIT.ordinal()] = 0;
        parserTable[NTID.C.ordinal()][TokenEnum.FLOATLIT.ordinal()] = 1;

        parserTable[NTID.EL.ordinal()][TokenEnum.ID.ordinal()] = 1;
        parserTable[NTID.EL.ordinal()][TokenEnum.INTLIT.ordinal()] = 1;
        parserTable[NTID.EL.ordinal()][TokenEnum.LPAREN.ordinal()] = 1;
        parserTable[NTID.EL.ordinal()][TokenEnum.RPAREN.ordinal()] = 0;
        parserTable[NTID.EL.ordinal()][TokenEnum.FLOATLIT.ordinal()] = 1;

        parserTable[NTID.ELT.ordinal()][TokenEnum.COMMA.ordinal()] = 0;
        parserTable[NTID.ELT.ordinal()][TokenEnum.RPAREN.ordinal()] = 1;

        parserTable[NTID.LV.ordinal()][TokenEnum.ID.ordinal()] = 0;

        parserTable[NTID.LVT.ordinal()][TokenEnum.EQ.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.SEMI.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.LBRACK.ordinal()] = 0;
        parserTable[NTID.LVT.ordinal()][TokenEnum.RBRACK.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.COMMA.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.ASSIGN.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.RPAREN.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.THEN.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.DO.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.TO.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.OR.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.AND.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.LESSEREQ.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.GREATEREQ.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.LESSER.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.GREATER.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.NEQ.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.MINUS.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.PLUS.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.DIV.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.MULT.ordinal()] = 2;
        parserTable[NTID.LVT.ordinal()][TokenEnum.PERIOD.ordinal()] = 1;
    }
}