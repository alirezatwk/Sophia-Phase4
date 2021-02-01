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

    private ArrayList<String> continueLabels;
    private ArrayList<String> breakLabels;

    static String methodHeader = ".limit stack 128\n" + ".limit locals 128";

    public CodeGenerator(Graph<String> classHierarchy) {
        this.labelCounter = 0;
        this.tempVariable = 0;
        this.currentSlot = new ArrayList<>();
        this.continueLabels = new ArrayList<>();
        this.breakLabels = new ArrayList<>();
        this.classHierarchy = classHierarchy;
        this.expressionTypeChecker = new ExpressionTypeChecker(classHierarchy);
        this.prepareOutputFolder();
    }

    private void prepareOutputFolder() {
        this.outputPath = "output/";
        String jasminPath = "utilities/jarFiles/jasmin.jar";
        String listClassPath = "utilities/codeGenerationUtilityClasses/List.j";
        String fptrClassPath = "utilities/codeGenerationUtilityClasses/Fptr.j";
        try {
            File directory = new File(this.outputPath);
            File[] files = directory.listFiles();
            if (files != null)
                for (File file : files)
                    file.delete();
            directory.mkdir();
        } catch (SecurityException e) {
        }
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
        } catch (IOException e) {
        }
    }

    private void createFile(String name) {
        try {
            String path = this.outputPath + name + ".j";
            File file = new File(path);
            file.createNewFile();
            FileWriter fileWriter = new FileWriter(path);
            this.currentFile = fileWriter;
        } catch (IOException e) {
        }
    }

    private void addCommand(String command) {
        try {
            command = String.join("\n\t\t", command.split("\n"));
            if (command.startsWith("Label_"))
                this.currentFile.write("\t" + command + "\n");
            else if (command.startsWith("."))
                this.currentFile.write(command + "\n");
            else
                this.currentFile.write("\t\t" + command + "\n");
            this.currentFile.flush();
        } catch (IOException e) {
        }
    }

    private String makeTypeSignature(Type t) {
        String typeString = "";
        if (t instanceof IntType)
            typeString += "Ljava/lang/Integer;";
        else if (t instanceof BoolType)
            typeString += "Ljava/lang/Boolean;";
        else if (t instanceof StringType)
            typeString += "Ljava/lang/String;";
        else if (t instanceof FptrType)
            typeString += "LFptr;";
        else if (t instanceof ListType)
            typeString += "LList;";
        else if (t instanceof ClassType)
            typeString += "L" + ((ClassType) t).getClassName().getName() + ";";
        else
            typeString += "V";
        return typeString;
    }

    private void initializeType(Type type) {
        if (type instanceof IntType) {
            addCommand("new java/lang/Integer");
            addCommand("dup");
            addCommand("iconst_0");
            addCommand("invokespecial java/lang/Integer/<init>(I)V");
        } else if (type instanceof BoolType) {
            addCommand("new java/lang/Boolean");
            addCommand("dup");
            addCommand("iconst_0");
            addCommand("invokespecial java/lang/Boolean/<init>(Z)V");
        } else if (type instanceof StringType) {
            addCommand("new java/lang/String");
            addCommand("dup");
            addCommand("ldc \"\"");
            addCommand("invokespecial java/lang/String/<init>(Ljava/lang/String;)V");
        } else if (type instanceof ListType) {
            addCommand("new List");
            addCommand("dup ");

            addCommand("new java/util/ArrayList");
            addCommand("dup ");
            addCommand("invokespecial java/util/ArrayList/<init>()V");
            ArrayList<ListNameType> listNameTypes = ((ListType) type).getElementsTypes();
            for (ListNameType listNameType : listNameTypes) {
                addCommand("dup");
                initializeType(listNameType.getType());
                addCommand("invokevirtual java/util/ArrayList/add(Ljava/lang/Object;)V");
            }
            addCommand("invokespecial List/<init>(Ljava/util/ArrayList;)V");
        } else if (type instanceof FptrType) {
            addCommand("aconst_null");
        } else if (type instanceof ClassType) {
            addCommand("aconst_null");
        }
    }

    private void addDefaultConstructor() {
        addCommand(".method public <init>()V\n" + methodHeader);
        addCommand("aload_0");
        if (this.currentClass.getParentClassName() == null)
            addCommand("invokespecial java/lang/Object/<init>()V");
        else
            addCommand("invokespecial " + currentClass.getParentClassName().getName() + "/<init>()V");
        for (FieldDeclaration fieldDeclaration : currentClass.getFields()) {
            addCommand("aload_0");
            initializeType(fieldDeclaration.getVarDeclaration().getType());
            addCommand("putfield " + currentClass.getClassName().getName() + "/" + fieldDeclaration.getVarDeclaration().getVarName().getName() + " " + makeTypeSignature(fieldDeclaration.getVarDeclaration().getType()));
        }
        addCommand("return");
        addCommand(".end method");
        addCommand("");
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
            tempVariable += 1;
            return currentSlot.size() + tempVariable - 1;
        }
        for (int i = 0; i < currentSlot.size(); i++)
            if (currentSlot.get(i).equals(identifier))
                return i;
        return 0;
    }

    private String betweenSlot(int slot) {
        if (slot > 4)
            return " ";
        else
            return "_";
    }

    @Override
    public String visit(Program program) {
        ArrayList<ClassDeclaration> classes = program.getClasses();
        for (ClassDeclaration classDec : classes) {
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
        if (classDeclaration.getParentClassName() == null)
            addCommand(".super java/lang/Object");
        else
            addCommand(".super " + classDeclaration.getParentClassName().getName());
        addCommand("");

        for (FieldDeclaration fieldDec : classDeclaration.getFields())
            fieldDec.accept(this);
        if (classDeclaration.getConstructor() != null) {
            this.currentMethod = classDeclaration.getConstructor();
            this.expressionTypeChecker.setCurrentMethod(classDeclaration.getConstructor());
            classDeclaration.getConstructor().accept(this);
        } else {
            this.addDefaultConstructor();
        }
        for (MethodDeclaration methodDec : classDeclaration.getMethods()) {
            this.currentMethod = methodDec;
            this.expressionTypeChecker.setCurrentMethod(methodDec);
            methodDec.accept(this);
        }
        if (classDeclaration.getClassName().getName().equals("Main"))
            addStaticMainMethod();
        return null;
    }

    @Override
    public String visit(ConstructorDeclaration constructorDeclaration) {
        if (constructorDeclaration.getArgs().size() != 0)
            addDefaultConstructor();
        this.visit((MethodDeclaration) constructorDeclaration);
        return null;
    }

    @Override
    public String visit(MethodDeclaration methodDeclaration) {
        currentSlot.clear();
        currentSlot.add("this");

        if (methodDeclaration instanceof ConstructorDeclaration) {
            String argString = "";
            for (VarDeclaration varDeclaration : methodDeclaration.getArgs())
                argString += makeTypeSignature(varDeclaration.getType());
            addCommand(".method <init>(" + argString + ')' + 'V' + '\n' + methodHeader);
            addCommand("aload_0");
            if (this.currentClass.getParentClassName() == null)
                addCommand("invokespecial java/lang/Object/<init>()V");
            else
                addCommand("invokespecial " + currentClass.getParentClassName().getName() + "/<init>()V");

            for (FieldDeclaration fieldDeclaration : currentClass.getFields()) {
                addCommand("aload_0");
                initializeType(fieldDeclaration.getVarDeclaration().getType());
                addCommand("putfield " + currentClass.getClassName().getName() + "/" + fieldDeclaration.getVarDeclaration().getVarName().getName() + " " + makeTypeSignature(fieldDeclaration.getVarDeclaration().getType()));
            }
        } else {
            String argString = "";
            for (VarDeclaration varDeclaration : methodDeclaration.getArgs())
                argString += makeTypeSignature(varDeclaration.getType());
            String returnString = makeTypeSignature(methodDeclaration.getReturnType());
            addCommand(".method " + methodDeclaration.getMethodName().getName() + "(" + argString + ")" + returnString + '\n' + methodHeader);
        }
        for (VarDeclaration varDeclaration : methodDeclaration.getArgs())
            currentSlot.add(varDeclaration.getVarName().getName());
        for (VarDeclaration varDeclaration : methodDeclaration.getLocalVars())
            varDeclaration.accept(this);
        for (Statement statement : methodDeclaration.getBody())
            statement.accept(this);

        if (!methodDeclaration.getDoesReturn())
            addCommand("return");
        addCommand(".end method");
        addCommand("");
        return null;
    }

    @Override
    public String visit(FieldDeclaration fieldDeclaration) {
        //todo
        String command = "";
        command += ".field ";
        command += fieldDeclaration.getVarDeclaration().getVarName().getName() + ' ';
        command += makeTypeSignature(fieldDeclaration.getVarDeclaration().getType());
        addCommand(command);
        return null;
    }

    @Override
    public String visit(VarDeclaration varDeclaration) {
        int slot = currentSlot.size();
        currentSlot.add(varDeclaration.getVarName().getName());
        initializeType(varDeclaration.getType());
        addCommand("astore" + betweenSlot(slot) + slot);
        return null;
    }

    @Override
    public String visit(AssignmentStmt assignmentStmt) {
        //todo
        BinaryExpression binaryExpression = new BinaryExpression(assignmentStmt.getlValue(), assignmentStmt.getrValue(), BinaryOperator.assign);
        addCommand(binaryExpression.accept(this));
        addCommand("pop");
        return null;
//        int slot = this.slotOf(assignmentStmt.getlValue().toString());
//        Value lValue = (Value)assignmentStmt.getlValue();
//        if (lValue instanceof IntValue)
//        {
//            String command = "";
//            command += "iload";
//            command += slot < 4 ? "_" + Integer.toString(slot) : " " + Integer.toString(slot);
//            command += "\n";
//            Expression rValue = assignmentStmt.getrValue();
//            command += "iconst";
//            int rv = Integer.parseInt(assignmentStmt.getrValue().toString());
//            command += rv < 4 ? "_" + Integer.toString(rv) : " " + Integer.toString(rv);
//            command += "\n";
//            command += "istore";
//            command += slot < 4 ? "_" + Integer.toString(slot) : " " + Integer.toString(slot);
//            addCommand(command);
//        }
//        else if (lValue instanceof BoolValue)
//        {
////            here Under Construction
//        }
//        else if (lValue instanceof StringValue)
//        {
//
//        }
//        else if (lValue instanceof ListValue)
//        {
//
//        }
//        Expression lValue = assignmentStmt.getlValue();
//        Expression rValue = assignmentStmt.getrValue();
//        rValue.accept(this);
//        lValue.accept(this);
//        addCommand("pop");
//        return null;
    }

    @Override
    public String visit(BlockStmt blockStmt) {
        //todo
        for (Statement stmt : blockStmt.getStatements()) {
            stmt.accept(this);
        }
        return null;
    }

    @Override
    public String visit(ConditionalStmt conditionalStmt) {
        // TODO
        return null;
    }

    @Override
    public String visit(MethodCallStmt methodCallStmt) {
        expressionTypeChecker.setIsInMethodCallStmt(true);
        addCommand(methodCallStmt.getMethodCall().accept(this));
        expressionTypeChecker.setIsInMethodCallStmt(false);
        addCommand("pop");
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
        if (type instanceof NullType) {
            addCommand("return");
        } else if (type instanceof IntType) {
            addCommand("new java/lang/Integer");
            addCommand("dup");
            addCommand(returnStmt.getReturnedExpr().accept(this));
            addCommand("invokespecial java/lang/Integer/<init>(I)V");
            addCommand("areturn");
        } else if (type instanceof BoolType) {
            addCommand("new java/lang/Boolean");
            addCommand("dup");
            addCommand(returnStmt.getReturnedExpr().accept(this));
            addCommand("invokespecial java/lang/Boolean/<init>(Z)V");
            addCommand("areturn");
        } else {
            addCommand(returnStmt.getReturnedExpr().accept(this));
            addCommand("areturn");
        }
        return null;
    }

    @Override
    public String visit(BreakStmt breakStmt) {
        addCommand("goto " + breakLabels.get(breakLabels.size() - 1));
        return null;
    }

    @Override
    public String visit(ContinueStmt continueStmt) {
        addCommand("goto " + continueLabels.get(continueLabels.size() - 1));
        return null;
    }

    @Override
    public String visit(ForeachStmt foreachStmt) {
        //todo
        return null;
    }

    @Override
    public String visit(ForStmt forStmt) {
        if (forStmt.getInitialize() != null)
            addCommand(forStmt.getInitialize().accept(this));

        addCommand("Label" + Integer.toString(this.labelCounter + 3) + ":");
        if (forStmt.getCondition() != null)
            branch(forStmt.getCondition(), "Label" + Integer.toString(this.labelCounter), "Label" + Integer.toString(this.labelCounter + 1));

        addCommand("Label" + Integer.toString(this.labelCounter) + ":");
        if (forStmt.getBody() != null){
            continueLabels.add("Label" + Integer.toString(this.labelCounter + 2));
            breakLabels.add("Label" + Integer.toString(this.labelCounter + 1));
            forStmt.getBody().accept(this);
            continueLabels.remove(continueLabels.size() - 1);
            breakLabels.remove(breakLabels.size() - 1);
        }

        addCommand("Label" + Integer.toString(this.labelCounter + 2) + ":");
        if (forStmt.getUpdate() != null)
            forStmt.getUpdate().accept(this);
        addCommand("goto Label" + Integer.toString(this.labelCounter + 3) + ":");

        addCommand("Label" + Integer.toString(this.labelCounter + 1) + ":");
        this.labelCounter += 4;
        return null;
    }

    @Override
    public String visit(BinaryExpression binaryExpression) {
        BinaryOperator operator = binaryExpression.getBinaryOperator();
        String commands = "";
        commands += binaryExpression.getFirstOperand().accept(this);
        commands += binaryExpression.getSecondOperand().accept(this);
        if (operator == BinaryOperator.add) {
            commands += "iadd\n";
        } else if (operator == BinaryOperator.sub) {
            commands += "isub\n";
        } else if (operator == BinaryOperator.mult) {
            commands += "imul\n";
        } else if (operator == BinaryOperator.div) {
            commands += "idiv\n";
        } else if (operator == BinaryOperator.mod) {
            commands += "irem\n";
        } else if ((operator == BinaryOperator.gt) || (operator == BinaryOperator.lt)) {
            commands += "if_icmp" + operator.toString() + " Label" + Integer.toString(this.labelCounter);
            commands += '\n';
            commands += "iconst_0\n";
            commands += "goto Label" + Integer.toString(this.labelCounter + 1);
            commands += '\n';
            commands += "Label" + Integer.toString(this.labelCounter) + ":\n";
            commands += "iconst_1\n";
            commands += "Label" + Integer.toString(this.labelCounter + 1) + ":\n";
            this.labelCounter += 2;
        } else if ((operator == BinaryOperator.eq) || (operator == BinaryOperator.neq)) {
            commands += "if_icmp" + operator.toString().substring(0, 2) + " Label" + Integer.toString(this.labelCounter);
            commands += '\n';
            commands += "iconst_0\n";
            commands += "goto Label" + Integer.toString(this.labelCounter + 1);
            commands += '\n';
            commands += "Label" + Integer.toString(this.labelCounter) + ":\n";
            commands += "iconst_1\n";
            commands += "Label" + Integer.toString(this.labelCounter + 1) + ":\n";
            this.labelCounter += 2;
        } else if (operator == BinaryOperator.and) {
            commands += "iand\n";
        } else if (operator == BinaryOperator.or) {
            commands += "ior\n";
        } else if (operator == BinaryOperator.assign) {
            Type firstType = binaryExpression.getFirstOperand().accept(expressionTypeChecker);
            String secondOperandCommands = binaryExpression.getSecondOperand().accept(this);
            if (firstType instanceof ListType) {
                //todo make new list with List copy constructor with the second operand commands
                // (add these commands to secondOperandCommands)
            }
            if (binaryExpression.getFirstOperand() instanceof Identifier) {
                //todo
            } else if (binaryExpression.getFirstOperand() instanceof ListAccessByIndex) {
                //todo
            } else if (binaryExpression.getFirstOperand() instanceof ObjectOrListMemberAccess) {
                Expression instance = ((ObjectOrListMemberAccess) binaryExpression.getFirstOperand()).getInstance();
                Type memberType = binaryExpression.getFirstOperand().accept(expressionTypeChecker);
                String memberName = ((ObjectOrListMemberAccess) binaryExpression.getFirstOperand()).getMemberName().getName();
                Type instanceType = instance.accept(expressionTypeChecker);
                if (instanceType instanceof ListType) {
                    //todo
                } else if (instanceType instanceof ClassType) {
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
        if (operator == UnaryOperator.minus) {
            commands += "ineg\n";
        } else if (operator == UnaryOperator.not) {
            commands += "ifeq" + " Label" + Integer.toString(this.labelCounter);
            commands += '\n';
            commands += "iconst_0\n";
            commands += "goto Label" + Integer.toString(this.labelCounter + 1);
            commands += '\n';
            commands += "Label" + Integer.toString(this.labelCounter) + ":\n";
            commands += "iconst_1\n";
            commands += "Label" + Integer.toString(this.labelCounter + 1) + ":\n";
            this.labelCounter += 2;
        } else if ((operator == UnaryOperator.predec) || (operator == UnaryOperator.preinc)) {
            if (unaryExpression.getOperand() instanceof Identifier) {
                //todo
            } else if (unaryExpression.getOperand() instanceof ListAccessByIndex) {
                //todo
            } else if (unaryExpression.getOperand() instanceof ObjectOrListMemberAccess) {
                Expression instance = ((ObjectOrListMemberAccess) unaryExpression.getOperand()).getInstance();
                Type memberType = unaryExpression.getOperand().accept(expressionTypeChecker);
                String memberName = ((ObjectOrListMemberAccess) unaryExpression.getOperand()).getMemberName().getName();
                Type instanceType = instance.accept(expressionTypeChecker);
                if (instanceType instanceof ListType) {
                    //todo
                } else if (instanceType instanceof ClassType) {
                    //todo
                }
            }
        } else if ((operator == UnaryOperator.postdec) || (operator == UnaryOperator.postinc)) {
            if (unaryExpression.getOperand() instanceof Identifier) {
                //todo
            } else if (unaryExpression.getOperand() instanceof ListAccessByIndex) {
                //todo
            } else if (unaryExpression.getOperand() instanceof ObjectOrListMemberAccess) {
                Expression instance = ((ObjectOrListMemberAccess) unaryExpression.getOperand()).getInstance();
                Type memberType = unaryExpression.getOperand().accept(expressionTypeChecker);
                String memberName = ((ObjectOrListMemberAccess) unaryExpression.getOperand()).getMemberName().getName();
                Type instanceType = instance.accept(expressionTypeChecker);
                if (instanceType instanceof ListType) {
                    //todo
                } else if (instanceType instanceof ClassType) {
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
        if (instanceType instanceof ClassType) {
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
        } else if (instanceType instanceof ListType) {
            //todo
        }
        return commands;
    }

    @Override
    public String visit(Identifier identifier) {
        String commands = "";
        return commands;
    }

    @Override
    public String visit(ListAccessByIndex listAccessByIndex) {
        String commands = "";
        commands += listAccessByIndex.getInstance().accept(this);
        commands += listAccessByIndex.getIndex().accept(this);
        commands += "invokevirtual List/getElement(I)Ljava/lang/Object;\n";

        Type elementType = ((ListType) listAccessByIndex.getInstance().accept(expressionTypeChecker)).getElementsTypes().get(((IntValue) listAccessByIndex.getIndex()).getConstant()).getType();

        if (elementType instanceof IntType) {
            commands += "checkcast java/lang/Integer\n";
            commands += "invokevirtual java/lang/Integer/intValue()I\n";
        } else if (elementType instanceof BoolType) {
            commands += "checkcast java/lang/Boolean\n";
            commands += "invokevirtual java/lang/Boolean/booleanValue()Z\n";
        } else if (elementType instanceof StringType) {
            commands += "checkcast java/lang/String\n";
        } else if (elementType instanceof ClassType) {
            commands += "checkcast " + ((ClassType) elementType).getClassName().getName() + "\n";
        }
        return commands;
    }

    @Override
    public String visit(MethodCall methodCall) {
        String commands = "";
        commands += methodCall.getInstance().accept(this);
        commands += "new java/util/ArrayList\n";
        commands += "dup\n";
        commands += "invokespecial java/util/ArrayList/<init>()V\n";
        for (Expression arg : methodCall.getArgs()) {
            Type argType = arg.accept(expressionTypeChecker);
            if (argType instanceof IntType) {
                commands += "new java/lang/Integer\n";
                commands += "dup\n";
                commands += arg.accept(this);
                commands += "invokespecial java/lang/Integer/<init>(I)V\n";
            } else if (argType instanceof BoolType) {
                commands += "new java/lang/Boolean\n";
                commands += "dup\n";
                commands += arg.accept(this);
                commands += "invokespecial java/lang/Boolean/<init>(Z)V\n";
            } else {
                commands += arg.accept(this);
            }
            commands += "invokevirtual java/util/ArrayList/add(Ljava/lang/Object;)Z\n";
            commands += "pop\n";
        }
        commands += "invokevirtual Fptr/invoke(Ljava/util/ArrayList;)Ljava/lang/Object;\n";
        Type returnType = ((FptrType) methodCall.getInstance().accept(expressionTypeChecker)).getReturnType();
        if (returnType instanceof IntType) {
            commands += "checkcast java/lang/Integer\n";
            commands += "invokevirtual java/lang/Integer/intValue()I\n";
        } else if (returnType instanceof BoolType) {
            commands += "checkcast java/lang/Boolean\n";
            commands += "invokevirtual java/lang/Boolean/booleanValue()Z\n";
        } else if (returnType instanceof StringType) {
            commands += "checkcast java/lang/String\n";
        } else if (returnType instanceof ClassType) {
            commands += "checkcast " + ((ClassType) returnType).getClassName().getName() + "\n";
        } else if (returnType instanceof NullType) {
            commands += "pop\n";
        }
        return commands;
    }

    @Override
    public String visit(NewClassInstance newClassInstance) {
        String commands = "new " + newClassInstance.getClass().getName() + '\n';
        commands += "dup\n";
        String argString = "";

        for (Expression arg : newClassInstance.getArgs()) {
            Type argType = arg.accept(expressionTypeChecker);
            if (argType instanceof IntType) {
                commands += "new java/lang/Integer\n";
                commands += "dup\n";
                commands += arg.accept(this);
                commands += "invokespecial java/lang/Integer/<init>(I)V\n";
            } else if (argType instanceof BoolType) {
                commands += "new java/lang/Boolean\n";
                commands += "dup\n";
                commands += arg.accept(this);
                commands += "invokespecial java/lang/Boolean/<init>(Z)V\n";
            } else {
                commands += arg.accept(this);
            }
            argString += makeTypeSignature(argType);
        }
        commands += "invokespecial " + newClassInstance.getClass().getName() + "/<init>(" + argString + ")V\n";
        return commands;
    }

    @Override
    public String visit(ThisClass thisClass) {
        String commands = "aload_0\n";
        return commands;
    }

    @Override
    public String visit(ListValue listValue) {
        String commands = "";
        commands += "new List\n";
        commands += "dup\n";

        commands += "new java/util/ArrayList\n";
        commands += "dup\n";

        for (Expression expr : listValue.getElements()) {
            Type exprType = expr.accept(expressionTypeChecker);
            if (exprType instanceof IntType) {
                commands += "new java/lang/Integer\n";
                commands += "dup\n";
                commands += expr.accept(this);
                commands += "invokespecial java/lang/Integer/<init>(I)V\n";
            } else if (exprType instanceof BoolType) {
                commands += "new java/lang/Boolean\n";
                commands += "dup\n";
                commands += expr.accept(this);
                commands += "invokespecial java/lang/Boolean/<init>(Z)V\n";
            } else {
                commands += expr.accept(this);
            }
        }
        commands += "invokespecial java/util/ArrayList/<init>()V\n";

        commands += "invokespecial List/<init>(Ljava/util/ArrayList;)V\n";
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