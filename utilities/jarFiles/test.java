import java.lang.Integer;

class Test{


    public static void main(String args[]){
        B b = new B();
        b.sibil();
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