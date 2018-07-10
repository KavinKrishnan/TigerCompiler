import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Pattern;

public class TigerScanner {
    private BufferedReader input;
    private int offset;

    private HashMap<String, TokenEnum> fixedTokens;

    private Queue<TigerToken> tokens;

    public TigerScanner(String codePath) throws FileNotFoundException, IOException {

        String fileStr = new String(Files.readAllBytes(Paths.get(codePath)));
        //fileStr = fileStr.replaceAll("\\/\\*.*\\*\\/", "");
        fileStr = fileStr.replaceAll("\\/\\*(.|\\n)*?\\*\\/", "");
        BufferedReader br = new BufferedReader(new StringReader(fileStr));

        input = br;
        offset = 0;


        //Initialize Hashmap for fixed size tokens
        fixedTokens = new HashMap<String, TokenEnum>();
        //non-words
        fixedTokens.put(",", TokenEnum.COMMA);
        fixedTokens.put(":", TokenEnum.COLON);
        fixedTokens.put(";", TokenEnum.SEMI);
        fixedTokens.put("(", TokenEnum.LPAREN);
        fixedTokens.put(")", TokenEnum.RPAREN);
        fixedTokens.put("[", TokenEnum.LBRACK);
        fixedTokens.put("]", TokenEnum.RBRACK);
        fixedTokens.put("{", TokenEnum.LBRACE);
        fixedTokens.put("}", TokenEnum.RBRACE);
        fixedTokens.put(".", TokenEnum.PERIOD);

        fixedTokens.put("+", TokenEnum.PLUS);
        fixedTokens.put("-", TokenEnum.MINUS);
        fixedTokens.put("*", TokenEnum.MULT);
        fixedTokens.put("/", TokenEnum.DIV);
        fixedTokens.put("=", TokenEnum.EQ);
        fixedTokens.put("<>", TokenEnum.NEQ);
        fixedTokens.put("<", TokenEnum.LESSER);
        fixedTokens.put(">", TokenEnum.GREATER);
        fixedTokens.put("<=", TokenEnum.LESSEREQ);
        fixedTokens.put(">=", TokenEnum.GREATEREQ);
        fixedTokens.put("&", TokenEnum.AND);
        fixedTokens.put("|", TokenEnum.OR);
        fixedTokens.put(":=", TokenEnum.ASSIGN);

        //words
        fixedTokens.put("array", TokenEnum.ARRAY);
        fixedTokens.put("record", TokenEnum.RECORD);
        fixedTokens.put("break", TokenEnum.BREAK);
        fixedTokens.put("do", TokenEnum.DO);
        fixedTokens.put("else", TokenEnum.ELSE);
        fixedTokens.put("end", TokenEnum.END);
        fixedTokens.put("for", TokenEnum.FOR);
        fixedTokens.put("function", TokenEnum.FUNC);
        fixedTokens.put("if", TokenEnum.IF);
        fixedTokens.put("in", TokenEnum.IN);
        fixedTokens.put("let", TokenEnum.LET);
        fixedTokens.put("of", TokenEnum.OF);
        fixedTokens.put("then", TokenEnum.THEN);
        fixedTokens.put("to", TokenEnum.TO);

        fixedTokens.put("type", TokenEnum.TYPE);
        fixedTokens.put("var", TokenEnum.VAR);
        fixedTokens.put("while", TokenEnum.WHILE);
        fixedTokens.put("endif", TokenEnum.ENDIF);
        fixedTokens.put("begin", TokenEnum.BEGIN);
        fixedTokens.put("end", TokenEnum.END);
        fixedTokens.put("enddo", TokenEnum.ENDDO);

        fixedTokens.put("int", TokenEnum.INT);
        fixedTokens.put("float", TokenEnum.FLOAT);
        fixedTokens.put("return", TokenEnum.RETURN);


        //Initialize tokens queue
        tokens = new LinkedList<TigerToken>();

        this.scan();
    }

    /**
     * Scans and prints out all the tokens of the Buffered Reader
     */
    public void scan() {
        //TODO: Handle Comments
        String line = "";
        int lineNum = 1;
        int charNum = 1;

        try {
            while ((line = input.readLine()) != null) {

                //Current Index to look at
                int currIndex = 0;

                //Current Possible Largest Match and Token
                String currentMatch = "";
                TigerToken currentToken = null;

                //Allows you to test a potential match larger than our current predicted largest match
                String potentialMatch = "";

                //Iterates through entire current line to find longest matches
                while (currIndex < line.length()) {
                    char currChar = line.charAt(currIndex);

                    //Handles a space, meaning we may have found a match
                    if (currChar == ' ' || currChar == '\t') {
                        if (currentMatch.equals("")) {
                            //Ignore the space
                            //Increment to continue algorithm on next character
                            currIndex++;
                        } else {
                            //We found the longest match, need to restart the algorithm
                            tokens.add(currentToken);

                            //--Restarting Algorithm Using current character as start--

                            //Current Possible Match and Token
                            currentMatch = "";
                            currentToken = null;

                            //Allows you to test a potential match larger than our current match
                            potentialMatch = "";

                            //Unlike code to handle printing at the bottom, increment because spaces cannot start a new match
                            currIndex++;
                        }
                    } else {

                        //Test potential match (equal to current match) appended with current character
                        potentialMatch += currChar;

                        //See if a token is returned with the current string
                        TigerToken isMatch = matchToken(potentialMatch, lineNum);

                        if (isMatch != null) {
                            //If the current potential match
                            currentMatch = potentialMatch;
                            currentToken = isMatch;
                            currIndex++;
                        } else {

                            String lookAheadMatch = null;
                            if (currIndex + 1 < line.length()) {
                                //We need to check if in see if the next index allows a match
                                lookAheadMatch = potentialMatch + line.charAt(currIndex + 1);
                            }

                            if (lookAheadMatch != null && (matchToken(lookAheadMatch, lineNum) != null)) {
                                //We can progress since we know the next character allows a match
                                currIndex++;
                            } else {
                                //We need output the longest token we got

                                if (currentMatch.equals("")) {
                                    //Current character was our start, and did not have a match
                                    String errorMessage = "Scanner error (line " + lineNum + "): \"" + currChar + "\" does not begin a valid token.";
                                    tokens.add(new TigerToken(errorMessage, null, lineNum));

                                    //--Restarting Algorithm Using current character as start--

                                    //Current Possible Match and Token
                                    currentMatch = "";
                                    currentToken = null;

                                    //Allows you to test a potential match larger than our current match
                                    potentialMatch = "";

                                    //We need to continue algorithm on the next character
                                    currIndex++;
                                } else {
                                    //We found the longest match, need to restart the algorithm
                                    tokens.add(currentToken);

                                    //--Restarting Algorithm Using current character as start--

                                    //Current Possible Match and Token
                                    currentMatch = "";
                                    currentToken = null;

                                    //Allows you to test a potential match larger than our current match
                                    potentialMatch = "";
                                }
                            }
                        }
                    }
                }

                //If we reached the end of the line and have found a valid match to end the line
                if (!currentMatch.equals("")) {
                    //System.out.print(currentToken.getToken().name() + " ");
                    tokens.add(currentToken);
                }

                //System.out.println();
                lineNum++;
            }
            tokens.add(new TigerToken("$", TokenEnum.ENDOFINPUT, lineNum));

        } catch (IOException e) {
        }
    }

    /**
     * Checks if the string passed in correlates to a valid token
     * @param lit  string to check for matching token
     * @param lineNum the line number this token appears in
     * @return  a token if one exists for the current string, null if not
     */
    public TigerToken matchToken(String lit, int lineNum) {

        //-- Fixed size token check --
        //Is it one of the other Tokens
        TokenEnum fixCheck = fixedTokens.get(lit);

        if (fixCheck != null) {
            return new TigerToken(lit, fixCheck, lineNum);
        }


        //-- Varying size token check --
        //INTLIT check
        if (Pattern.compile("^(([1-9][0-9]*)|0)$").matcher(lit).find()) {
            return new TigerToken(lit, TokenEnum.INTLIT, lineNum);
        }

        //FLOATLIT check
        if (Pattern.compile("^(([1-9][0-9]*)|0)\\.[0-9]+$").matcher(lit).find()) {
            return new TigerToken(lit, TokenEnum.FLOATLIT, lineNum);
        }

        //ID check
        if (Pattern.compile("^[a-zA-Z]([a-zA-Z0-9]|_)*$").matcher(lit).find()) {
            return new TigerToken(lit, TokenEnum.ID, lineNum);
        }

        return null;
    }

    /**
     * Method to print errors/token and get the next valid token
     * @return the next valid token
     */
    public TigerToken getNext() {
        //Print the token and return it.
        TigerToken token;
        while (true) {
            token = tokens.remove();
            if (token.getToken() == null) {
                System.out.println();
                System.out.println(token.getLit());
            } else {
                System.out.print(token.getToken().name() + " ");
                break;
            }
        }
        return token;
    }

}
