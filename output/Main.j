.class Main
.super java/lang/Object
		
.method <init>()V
		.limit stack 128
		.limit locals 128
		aload_0
		invokespecial java/lang/Object/<init>()V
		getstatic java/lang/System/out Ljava/io/PrintStream;
		bipush 100
		ineg
		bipush 10
		iadd
		bipush 20
		iadd
		bipush 10
		bipush 32
		imul
		bipush 7
		idiv
		isub
		bipush 32
		iconst_3
		irem
		iadd
		invokevirtual java/io/PrintStream/print(I)V
		return
.end method
.method public static main([Ljava/lang/String;)V
		.limit stack 128
		.limit locals 128
		new Main
		invokespecial Main/<init>()V
		return
.end method
		
