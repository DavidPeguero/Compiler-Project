package plc.project;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    // Print items in list
    private void printList(List<Ast.Expression> expressionList){
        if (expressionList.isEmpty())
            return;
        print(expressionList.getFirst());
        for (int i = 1; i < expressionList.size(); i++){
            print(", ");
            print(expressionList.get(i));
        }
        return;
    }

    private void printStatements(List<Ast.Statement> statementsList){
        statementsList.forEach(statement -> {
                newline(indent);
                print(statement);
            }
        );
    }

    // Structurally the same as printStatements but due to type
    // differences must be done in a separate function
    private void printCases(List<Ast.Statement.Case> cases) {
        cases.forEach(aCase -> {
                newline(indent);
                print(aCase);
            }
        );
    }

    @Override
    public Void visit(Ast.Source ast) {
        print("public class Main {");
        if(!ast.getGlobals().isEmpty()) {
            newline(0);
            newline(++indent);
            for (int i = 0; i < ast.getGlobals().size(); i++) {
                visit(ast.getGlobals().get(i));
                if (i != ast.getGlobals().size() - 1) {
                    newline(indent);
                }
            }
            indent--;
        }
        indent++;
        newline(0);
        newline(indent);
        print("public static void main(String[] args) {");
        indent++;
        newline(indent);
        print("System.exit(new Main().main());");
        indent--;
        newline(indent);
        print("}");
        newline(0);
        newline(indent);
        //Possible we need to add a newLine after each function
        for(int i = 0; i < ast.getFunctions().size(); i++){
            visit(ast.getFunctions().get(i));
            if(i != ast.getFunctions().size() - 1){
                newline(0);
                newline(indent);
            }
        }
        indent--;
        newline(0);
        newline(indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {
        if(ast.getMutable()){
            if(ast.getValue().isPresent() && ast.getValue().get() instanceof Ast.Expression.PlcList) {
                print(ast.getVariable().getType().getJvmName() + "[] " + ast.getName() + " = ",
                        ast.getValue().get(),
                        ";");
            }
            else if(ast.getValue().isPresent()) {
                print(ast.getVariable().getType().getJvmName() + " " + ast.getName() + " = " , ast.getValue().get(), ";");
            } else{
                print(ast.getVariable().getType().getJvmName() + " " + ast.getName() + ";");
            }
        } else{
            print("final " + ast.getVariable().getType().getJvmName() + " " + ast.getName() + " = ", ast.getValue().get(), ";");

        }

        return null;
    }

    @Override
    public Void visit(Ast.Function ast) {
        print(ast.getFunction().getReturnType().getJvmName() + " " + ast.getFunction().getJvmName() + "(");
        List<Environment.Type> pTypes = ast.getFunction().getParameterTypes();
        List<String> parameters = ast.getParameters();

        for(int i = 0; i < parameters.size() - 1; i++){
            print(pTypes.get(i).getJvmName() + " " + parameters.get(i) + ", ");
        }
        if(!pTypes.isEmpty())
            print(pTypes.getLast().getJvmName() + " " + parameters.getLast());
        print(") {");
        if(ast.getStatements().isEmpty()){
            print("}");
            return null;
        }
        indent++;
        newline(indent);
        for(int i = 0; i < ast.getStatements().size(); i++){
            visit(ast.getStatements().get(i));
            if(i != ast.getStatements().size() - 1){
                newline(indent);
            }
        }
        indent--;
        newline(indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        print(ast.getExpression());
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        if(ast.getValue().isPresent()) {
            print(ast.getVariable().getType().getJvmName() + " " + ast.getName() + " = ");
            print(ast.getValue().get());
            print(";");
        }else{
            print(ast.getVariable().getType().getJvmName() + " " + ast.getName() + ";");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        print(ast.getReceiver());
        print(" = ");
        print(ast.getValue());
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        print("if (");
        print(ast.getCondition());
        print(") {");
        indent++;
        printStatements(ast.getThenStatements());
        newline(--indent);
        print("}");

        if (ast.getElseStatements().isEmpty())
            return null;

        print(" else {");
        indent++;
        printStatements(ast.getElseStatements());
        newline(--indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        print("switch (");
        print(ast.getCondition());
        print(") {");

        indent++;
        printCases(ast.getCases());
        newline(--indent);
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        indent++;
        if (ast.getValue().isPresent()) {
            print("case ");
            print(ast.getValue().get());
            print(":");
            printStatements(ast.getStatements());
            newline(indent);
            print("break;");
        } else{
            print("default:");
            printStatements(ast.getStatements());
        }

        indent--;
        return null;
    }

    // Nothing actually tests this function
    // Check lectures to see how professor implemented this
    @Override
    public Void visit(Ast.Statement.While ast) {
        print("while (");
        print(ast.getCondition());
        print(") {");

        if (ast.getStatements().isEmpty()) {
            print("}");
            return null;
        }

        // Print inner statements on new scope
        indent++;
        printStatements(ast.getStatements());
        newline(--indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        print("return ");
        print(ast.getValue());
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        switch(ast.getLiteral()){
            case String s :
                print("\"");
                print(s);
                print("\"");
                break;
            case Character c:
                print("'");
                print(c);
                print("'");
                break;
            case null:
                print("null");
                break;
            default: // Should print boolean or int or BigDecimal
                print(ast.getLiteral());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        print("(");
        print(ast.getExpression());
        print(")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        if (ast.getOperator().equals("^")){
            print("Math.pow(");
            printList(Arrays.asList(ast.getLeft(), ast.getRight()));
            print(")");
        }
        else{
            print(ast.getLeft());
            print(" ");
            print(ast.getOperator());
            print(" ");
            print(ast.getRight());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        print(ast.getVariable().getJvmName());

        if (ast.getOffset().isPresent()){
            print("[");
            print(ast.getOffset().get());
            print("]");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        print(ast.getFunction().getJvmName());
        print("(");
        printList(ast.getArguments());
        print(")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        print("{");
        printList(ast.getValues());
        print("}");
        return null;
    }

}
