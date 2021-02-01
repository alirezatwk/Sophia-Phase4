.class Test
.super java/lang/Object
		
.method public <init>()V
		.limit stack 128
		.limit locals 128
		aload_0
		invokespecial java/lang/Object/<init>()V
		return
.end method
		
.method <init>(Ljava/lang/String;)V
		.limit stack 128
		.limit locals 128
		aload_0
		invokespecial java/lang/Object/<init>()V
		getstatic java/lang/System/out Ljava/io/PrintStream;
		iconst_3
		invokevirtual java/io/PrintStream/print(I)V
		return
.end method
		
