// "Replace with lambda" "true"
import java.util.*;
class Test2 {

    interface I<X> {
        X foo(List<X> list);
    }

    static <T> I<T> bar(I<T> i){return i;}
 
    {
        bar(new I<Stri<caret>ng>() {
            @Override
            public String foo(List<String> list) {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }
        });
    }
}