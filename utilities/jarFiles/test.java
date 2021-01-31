import java.lang.Integer;

class Test{

    void Test(){
        System.out.print(3);
    }

    void Test(int a){
        System.out.print(3);
    }

    public static void main(String args[]){
        Test t = new Test();
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