import jdk.nashorn.internal.parser.Token;

import java.util.ArrayList;
import java.util.HashMap;

public class TigerIRGenerator {
    private ArrayList<String> IR;
    private int currentTemp;
    private int currentLabel;
    private ArrayList<TigerToken> creatingVariables;
    private Boolean pastVar;
    private Boolean pastAss;
    private Boolean pastColon;
    private ArrayList<TigerToken> creatingFunction;

    String value;
    TigerToken type;


    public TigerIRGenerator() {
        IR = new ArrayList<String>();
        creatingVariables = new ArrayList<TigerToken>();
        creatingFunction = new ArrayList<TigerToken>();
        currentTemp = 0;
        currentLabel = 0;
        pastAss = false;
        pastVar = false;
        pastColon = false;
        value = "";
        type = null;
    }

    public void addToIR(String line) {
        IR.add(line);
    }

    public String toString() {
        String output = "";
        for (String entry: IR) {
            output = output + entry + "\n";
        }
        return output;
    }

    public String getCurrentTemp() {
        return "temp" + this.getCurrentTemp();
    }

    public String getCurrentLabel() {
        return "label" + this.getCurrentLabel();
    }

    public String getNextTemp() {
        this.currentTemp++;
        return "temp" + this.currentTemp;
    }

    public String getNextLabel() {
        this.currentLabel++;
        return "label" + this.currentLabel;
    }

    public void genVariables(SymbolTable st, TigerToken token) {
        if (token.getToken() == TokenEnum.VAR) {
            pastVar = true;
        }
        if (!pastVar) {
            return;
        }
        if (token.getToken() == TokenEnum.COLON) {
            pastColon = true;
        }
        else if (token.getToken() == TokenEnum.ID && !pastColon) {
            creatingVariables.add(token);
        }
        else if ((token.getToken() == TokenEnum.ID || token.getToken() == TokenEnum.FLOAT || token.getToken() == TokenEnum.INT ) && pastColon && !pastAss) {
            type = token;
        }
        else if (token.getToken() == TokenEnum.ASSIGN) {
            pastAss = true;
        }
        else if ((token.getToken() == TokenEnum.INTLIT || token.getToken() ==  TokenEnum.FLOATLIT )&& pastAss) {
            //flush the list of tokens ot the ir

            if (type.getToken() != TokenEnum.INT && type.getToken() != TokenEnum.FLOAT) {
                SymbolEntry symbol = st.get(type.getLit(), SymbolEntry.SymbolType.TYPE);
                if (symbol != null)
                {
                    TypeEntry tabletype = (TypeEntry)symbol;
                    if (tabletype.isArray) {
                        for (TigerToken t : creatingVariables) {
                            addToIR("assign, " + t.getLit() + ", " + tabletype.arrSize + ", " + token.getLit());
                        }
                    }
                }
            }
            else
            {
                for (TigerToken t : creatingVariables) {
                    addToIR("assign, " + t.getLit() + ", " + token.getLit());
                }
            }

            creatingVariables.clear();
            pastAss = false;
            pastVar = false;
            pastColon = false;
            value = "";
            type = null;

        }
    }
    public void genFunction(SymbolTable st, TigerToken token) {
        System.out.println(token.getLit() + " type " + token.getToken());
        // next token will be the name of the function
       // if (token.getToken() == TokenEnum.)
        //System.out.println("\nIN IR current token is " + token.getToken() + "\nlit is " + token.getLit());
        // handle variable declarations
    }
}

