package main.visitor.codeGenerator;

import main.ast.nodes.Program;
import main.ast.nodes.declaration.classDec.ClassDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.ConstructorDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.FieldDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.MethodDeclaration;
import main.ast.nodes.declaration.variableDec.VarDeclaration;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.expression.operators.UnaryOperator;
import main.ast.nodes.expression.values.ListValue;
import main.ast.nodes.expression.values.NullValue;
import main.ast.nodes.expression.values.primitive.BoolValue;
import main.ast.nodes.expression.values.primitive.IntValue;
import main.ast.nodes.expression.values.primitive.StringValue;
import main.ast.nodes.statement.*;
import main.ast.nodes.statement.loop.BreakStmt;
import main.ast.nodes.statement.loop.ContinueStmt;
import main.ast.nodes.statement.loop.ForStmt;
import main.ast.nodes.statement.loop.ForeachStmt;
import main.ast.types.NullType;
import main.ast.types.Type;
import main.ast.types.functionPointer.FptrType;
import main.ast.types.list.ListNameType;
import main.ast.types.list.ListType;
import main.ast.types.single.BoolType;
import main.ast.types.single.ClassType;
import main.ast.types.single.IntType;
import main.ast.types.single.StringType;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.ItemNotFoundException;
import main.symbolTable.items.ClassSymbolTableItem;
import main.symbolTable.items.FieldSymbolTableItem;
import main.symbolTable.utils.graph.Graph;
import main.symbolTable.utils.graph.exceptions.GraphDoesNotContainNodeException;
import main.visitor.Visitor;
import main.visitor.typeChecker.ExpressionTypeChecker;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;

public class CodeGenerator extends Visitor<String> {
    ExpressionTypeChecker expressionTypeChecker;
    Graph<String> classHierarchy;
    private String outputPath;
    private FileWriter currentFile;

    private ClassDeclaration currentClass;
    private MethodDeclaration currentMethod;

    private int labelCounter;

    private ArrayList<String> currentSlot;
    private int tempVariable;

    static String methodHeader = ".limit stack 128\n" + ".limit locals 128";

    public CodeGenerator(Graph<String> classHierarchy) {
        this.labelCounter = 0;
        this.tempVariable = 0;
        this.classHierarchy = classHierarchy;
        this.expressionTypeChecker = new ExpressionTypeChecker(classHierarchy);
        this.prepareOutputFolder();
    }

    private void prepareOutputFolder() {
        this.outputPath = "output/";
        String jasminPath = "utilities/jarFiles/jasmin.jar";
        String listClassPath = "utilities/codeGenerationUtilityClasses/List.j";
        String fptrClassPath = "utilities/codeGenerationUtilityClasses/Fptr.j";
        try{
            File directory = new File(this.outputPath);
            File[] files = directory.listFiles();
            if(files != null)
                for (File file : files)
                    file.delete();
            directory.mkdir();
        }
        catch(SecurityException e) { }
        copyFile(jasminPath, this.outputPath + "jasmin.jar");
        copyFile(listClassPath, this.outputPath + "List.j");
        copyFile(fptrClassPath, this.outputPath + "Fptr.j");
    }

    private void copyFile(String toBeCopied, String toBePasted) {
        try {
            File readingFile = new File(toBeCopied);
            File writingFile = new File(toBePasted);
            InputStream readingFileStream = new FileInputStream(readingFile);
            OutputStream writingFileStream = new FileOutputStream(writingFile);
            byte[] buffer = new byte[1024];
            int readLength;
            while ((readLength = readingFileStream.read(buffer)) > 0)
                writingFileStream.write(buffer, 0, readLength);
            readingFileStream.close();
            writingFileStream.close();
        } catch (IOException e) { }
    }

    private void createFile(String name) {
        try {
            String path = this.outputPath + name + ".j";
            File file = new File(path);
            file.createNewFile();
            FileWriter fileWriter = new FileWriter(path);
            this.currentFile = fileWriter;
        } catch (IOException e) {}
    }

    private void addCommand(String command) {
        try {
            command = String.join("\n\t\t", command.split("\n"));
            if(command.startsWith("Label_"))
                this.currentFile.write("\t" + command + "\n");
            else if(command.startsWith("."))
                this.currentFile.write(command + "\n");
            else
                this.currentFile.write("\t\t" + command + "\n");
            this.currentFile.flush();
        } catch (IOException e) {}
    }

    private String makeTypeSignature(Type t) {
        //todo
        return null;
    }

    private void addDefaultConstructor() {
        //todo
    }

    private void addStaticMainMethod() {
        addCommand(".method public static main([Ljava/lang/String;)V\n" + methodHeader);
        addCommand("new Main");
        addCommand("invokespecial Main/<init>()V");
        addCommand("return");
        addCommand(".end method");
        addCommand("");
    }

    private int slotOf(String identifier) {
        if (identifier.equals("")) {
            this.tempVariable += 1;
            return this.currentSlot.size() + this.tempVariable - 1;
        }
        for (int i = 0; i < this.currentSlot.size(); i++) {
            if (this.currentSlot.get(i).equals(identifier))
                return i;
        }
        return 0;
    }

    @Override
    public String visit(Program program) {
        ArrayList<ClassDeclaration> classes = program.getClasses();
        for (ClassDeclaration classDec : classes)
        {
            this.currentClass = classDec;
            this.expressionTypeChecker.setCurrentClass(classDec);
            classDec.accept(this);
        }
        return null;
    }

    @Override
    public String visit(ClassDeclaration classDeclaration) {
        createFile(classDeclaration.getClassName().getName());
        addCommand(".class " + classDeclaration.getClassName().getName());

        if(classDeclaration.getParentClassName() == null)
            addCommand(".super java/lang/Object");
        else
            addCommand(".super " + classDeclaration.getParentClassName().getName()); // Not sure.
        addCommand("");

        for(FieldDeclaration fieldDec : classDeclaration.getFields())
            fieldDec.accept(this);
        if(classDeclaration.getConstructor() != null)
        {
            this.currentMethod = classDeclaration.getConstructor();
            this.expressionTypeChecker.setCurrentMethod(classDeclaration.getConstructor());
            classDeclaration.getConstructor().accept(this);
        }
        else
        {
            this.addDefaultConstructor();
//            Are the next 3 lines needed?
            this.currentMethod = classDeclaration.getConstructor();
            this.expressionTypeChecker.setCurrentMethod(classDeclaration.getConstructor());
            if(classDeclaration.getConstructor() != null)
                classDeclaration.getConstructor().accept(this);
        }
        for(MethodDeclaration methodDec : classDeclaration.getMethods())
        {
            this.currentMethod = methodDec;
            this.expressionTypeChecker.setCurrentMethod(methodDec);
            methodDec.accept(this);
        }
        if(classDeclaration.getClassName().getName().equals("Main"))
            addStaticMainMethod();
        return null;
    }

    @Override
    public String visit(ConstructorDeclaration constructorDeclaration) {


        //todo add default constructor or static main method if needed
        this.visit((MethodDeclaration) constructorDeclaration);
        return null;
    }

    @Override
    public String visit(MethodDeclaration methodDeclaration) {
        //todo add method or constructor headers
        addCommand(".method " + /*methodDeclaration.getMethodName().getName()*/ "<init>" + '(' + ')' + 'V' + '\n' + methodHeader); // TODO: Add arguments and return type
        addCommand("aload_0");
        addCommand("invokespecial java/lang/Object/<init>()V");

        if(methodDeclaration instanceof ConstructorDeclaration) {
            //todo call parent constructor
            //todo initialize fields
        }
        for(Statement statement : methodDeclaration.getBody())
            statement.accept(this);
        //todo visit local vars and body and add return if needed
        addCommand("return"); // TODO: Check return type
        addCommand(".end method");
        return null;
    }

    @Override
    public String visit(FieldDeclaration fieldDeclaration) {
        //todo
        return null;
    }

    @Override
    public String visit(VarDeclaration varDeclaration) {
        //todo
        return null;
    }

    @Override
    public String visit(AssignmentStmt assignmentStmt) {
        //todo
        return null;
    }

    @Override
    public String visit(BlockStmt blockStmt) {
        //todo
        return null;
    }

    @Override
    public String visit(ConditionalStmt conditionalStmt) {
        //todo
        return null;
    }

    @Override
    public String visit(MethodCallStmt methodCallStmt) {
        //todo
        return null;
    }

    @Override
    public String visit(PrintStmt print) {
        addCommand("getstatic java/lang/System/out Ljava/io/PrintStream;");
        String expressionString = print.getArg().accept(this);
        addCommand(expressionString);
        Type expressionType = print.getArg().accept(expressionTypeChecker);
        if (expressionTypeChecker.isSameType(expressionType, new IntType()) || expressionTypeChecker.isSameType(expressionType, new BoolType()))
            addCommand("invokevirtual java/io/PrintStream/print(I)V");
        else if (expressionTypeChecker.isSameType(expressionType, new StringType()))
            addCommand("invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V");
        return null;
    }

    @Override
    public String visit(ReturnStmt returnStmt) {
        Type type = returnStmt.getReturnedExpr().accept(expressionTypeChecker);
        if(type instanceof NullType) {
            addCommand("return");
        }
        else {
            //todo add commands to return
        }
        return null;
    }

    @Override
    public String visit(BreakStmt breakStmt) {
        //todo
        return null;
    }

    @Override
    public String visit(ContinueStmt continueStmt) {
        //todo
        return null;
    }

    @Override
    public String visit(ForeachStmt foreachStmt) {
        //todo
        return null;
    }

    @Override
    public String visit(ForStmt forStmt) {
        //todo
        return null;
    }

    private int makeLabel(){
        addCommand("Label" + Integer.toString(this.labelCounter));
        this.labelCounter += 1;
        return this.labelCounter - 1;
    }

    @Override
    public String visit(BinaryExpression binaryExpression) {
        BinaryOperator operator = binaryExpression.getBinaryOperator();
        String commands = "";
        commands += binaryExpression.getFirstOperand().accept(this);
        commands += binaryExpression.getSecondOperand().accept(this);
        if (operator == BinaryOperator.add) {
            commands += "iadd\n";
        }
        else if (operator == BinaryOperator.sub) {
            commands += "isub\n";
        }
        else if (operator == BinaryOperator.mult) {
            commands += "imul\n";
        }
        else if (operator == BinaryOperator.div) {
            commands += "idiv\n";
        }
        else if (operator == BinaryOperator.mod) {
            commands += "irem\n";
        }
        else if((operator == BinaryOperator.gt) || (operator == BinaryOperator.lt)) {
            commands += "if_icmp" + operator.toString() + " Label" + Integer.toString(this.labelCounter);
            commands += '\n';
            commands += "iconst_0\n";
            commands += "goto Label" + Integer.toString(this.labelCounter + 1);
            commands += '\n';
            commands += "Label" + Integer.toString(this.labelCounter) + ":\n";
            commands += "iconst_1\n";
            commands += "Label" + Integer.toString(this.labelCounter + 1) + ":\n";
            this.labelCounter += 2;
        }
        else if((operator == BinaryOperator.eq) || (operator == BinaryOperator.neq)) {
            commands += "if_icmp" + operator.toString().substring(0, 2) + " Label" + Integer.toString(this.labelCounter);
            commands += '\n';
            commands += "iconst_0\n";
            commands += "goto Label" + Integer.toString(this.labelCounter + 1);
            commands += '\n';
            commands += "Label" + Integer.toString(this.labelCounter) + ":\n";
            commands += "iconst_1\n";
            commands += "Label" + Integer.toString(this.labelCounter + 1) + ":\n";
            this.labelCounter += 2;
        }
        else if(operator == BinaryOperator.and) {
            commands += "iand\n";
        }
        else if(operator == BinaryOperator.or) {
            commands += "ior\n";
        }
        else if(operator == BinaryOperator.assign) {
            Type firstType = binaryExpression.getFirstOperand().accept(expressionTypeChecker);
            String secondOperandCommands = binaryExpression.getSecondOperand().accept(this);
            if(firstType instanceof ListType) {
                //todo make new list with List copy constructor with the second operand commands
                // (add these commands to secondOperandCommands)
            }
            if(binaryExpression.getFirstOperand() instanceof Identifier) {
                //todo
            }
            else if(binaryExpression.getFirstOperand() instanceof ListAccessByIndex) {
                //todo
            }
            else if(binaryExpression.getFirstOperand() instanceof ObjectOrListMemberAccess) {
                Expression instance = ((ObjectOrListMemberAccess) binaryExpression.getFirstOperand()).getInstance();
                Type memberType = binaryExpression.getFirstOperand().accept(expressionTypeChecker);
                String memberName = ((ObjectOrListMemberAccess) binaryExpression.getFirstOperand()).getMemberName().getName();
                Type instanceType = instance.accept(expressionTypeChecker);
                if(instanceType instanceof ListType) {
                    //todo
                }
                else if(instanceType instanceof ClassType) {
                    //todo
                }
            }
        }
        return commands;
    }

    @Override
    public String visit(UnaryExpression unaryExpression) {
        UnaryOperator operator = unaryExpression.getOperator();
        String commands = "";
        commands += unaryExpression.getOperand().accept(this);
        if(operator == UnaryOperator.minus) {
            commands += "ineg\n";
        }
        else if(operator == UnaryOperator.not) {
            commands += "ifeq" + " Label" + Integer.toString(this.labelCounter);
            commands += '\n';
            commands += "iconst_0\n";
            commands += "goto Label" + Integer.toString(this.labelCounter + 1);
            commands += '\n';
            commands += "Label" + Integer.toString(this.labelCounter) + ":\n";
            commands += "iconst_1\n";
            commands += "Label" + Integer.toString(this.labelCounter + 1) + ":\n";
            this.labelCounter += 2;
        }
        else if((operator == UnaryOperator.predec) || (operator == UnaryOperator.preinc)) {
            if(unaryExpression.getOperand() instanceof Identifier) {
                //todo
            }
            else if(unaryExpression.getOperand() instanceof ListAccessByIndex) {
                //todo
            }
            else if(unaryExpression.getOperand() instanceof ObjectOrListMemberAccess) {
                Expression instance = ((ObjectOrListMemberAccess) unaryExpression.getOperand()).getInstance();
                Type memberType = unaryExpression.getOperand().accept(expressionTypeChecker);
                String memberName = ((ObjectOrListMemberAccess) unaryExpression.getOperand()).getMemberName().getName();
                Type instanceType = instance.accept(expressionTypeChecker);
                if(instanceType instanceof ListType) {
                    //todo
                }
                else if(instanceType instanceof ClassType) {
                    //todo
                }
            }
        }
        else if((operator == UnaryOperator.postdec) || (operator == UnaryOperator.postinc)) {
            if(unaryExpression.getOperand() instanceof Identifier) {
                //todo
            }
            else if(unaryExpression.getOperand() instanceof ListAccessByIndex) {
                //todo
            }
            else if(unaryExpression.getOperand() instanceof ObjectOrListMemberAccess) {
                Expression instance = ((ObjectOrListMemberAccess) unaryExpression.getOperand()).getInstance();
                Type memberType = unaryExpression.getOperand().accept(expressionTypeChecker);
                String memberName = ((ObjectOrListMemberAccess) unaryExpression.getOperand()).getMemberName().getName();
                Type instanceType = instance.accept(expressionTypeChecker);
                if(instanceType instanceof ListType) {
                    //todo
                }
                else if(instanceType instanceof ClassType) {
                    //todo
                }
            }
        }
        return commands;
    }

    @Override
    public String visit(ObjectOrListMemberAccess objectOrListMemberAccess) {
        Type memberType = objectOrListMemberAccess.accept(expressionTypeChecker);
        Type instanceType = objectOrListMemberAccess.getInstance().accept(expressionTypeChecker);
        String memberName = objectOrListMemberAccess.getMemberName().getName();
        String commands = "";
        if(instanceType instanceof ClassType) {
            String className = ((ClassType) instanceType).getClassName().getName();
            try {
                SymbolTable classSymbolTable = ((ClassSymbolTableItem) SymbolTable.root.getItem(ClassSymbolTableItem.START_KEY + className, true)).getClassSymbolTable();
                try {
                    classSymbolTable.getItem(FieldSymbolTableItem.START_KEY + memberName, true);
                    //todo it is a field
                } catch (ItemNotFoundException memberIsMethod) {
                    //todo it is a method (new instance of Fptr)
                }
            } catch (ItemNotFoundException classNotFound) {
            }
        }
        else if(instanceType instanceof ListType) {
            //todo
        }
        return commands;
    }

    @Override
    public String visit(Identifier identifier) {
        String commands = "";
        //todo
        return commands;
    }

    @Override
    public String visit(ListAccessByIndex listAccessByIndex) {
        String commands = "";
        //todo
        return commands;
    }

    @Override
    public String visit(MethodCall methodCall) {
        String commands = "";
        //todo
        return commands;
    }

    @Override
    public String visit(NewClassInstance newClassInstance) {
        String commands = "";
        //todo
        return commands;
    }

    @Override
    public String visit(ThisClass thisClass) {
        String commands = "";
        //todo
        return commands;
    }

    @Override
    public String visit(ListValue listValue) {
        String commands = "";
        //todo
        return commands;
    }

    @Override
    public String visit(NullValue nullValue) {
        String commands = "aconst_null\n";
        return commands;
    }

    @Override
    public String visit(IntValue intValue) {
        String commands = "";
        if (0 <= intValue.getConstant() && intValue.getConstant() <= 5)
            commands += "iconst_";
        else
            commands += "bipush ";
        commands += Integer.toString(intValue.getConstant());
        commands += '\n';
        return commands;
    }

    @Override
    public String visit(BoolValue boolValue) {
        String commands = "";
        if (boolValue.getConstant())
            commands += "iconst_1";
        else
            commands += "iconst_0";
        commands += '\n';
        return commands;
    }

    @Override
    public String visit(StringValue stringValue) {
        String commands = "ldc \"";
        commands += stringValue.getConstant();
        commands += "\"\n";
        return commands;
    }

}