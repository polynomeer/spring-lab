package lab.experiments.autoconfig;

class CustomGreetingService implements GreetingService {

    @Override
    public String greet(String name) {
        return "Yo, " + name;
    }
}
