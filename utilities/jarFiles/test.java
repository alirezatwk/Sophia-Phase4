import java.lang.Integer;

class Test{


    public static void main(String args[]){
        B a = new B();
        Object b = a;
        B c = (B) b;
    }

    void sum(int aa, Integer a, Boolean b, Test c, int d){
        System.out.print(-5);
    }

}

class B extends Test {
    void sibil(){
        System.out.print("sibiiil");
    }

}