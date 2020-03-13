package net.unicon.iam.cas.service.converter;


public class MainClass {

    static void main(String[] args) {
        def cli = new CliBuilder(
                header: '\nWelcome to CAS Service Converter!  Available options (use -help for help):\n',
                usage: 'src.main.groovy.net.unicon.iam.cas.service.converter.CASServiceConverter.groovy -currentformat cas3json|casjson|shibxml|shibmetadata -currentdir pathToFileOrDir -resultformat casjson|shibxml|shibmetadata -resultlocation pathToResultLocation',
                footer: '\nUsing those options above, CAS Service Converter will convert CAS services to the specified format and location.\n')

        cli.with {
            help(longOpt: 'help', 'Usage Information', required: false)
            currentformat(longOpt: 'currentformat', 'Current service format', args: 1, required: true)
            currentdir(longOpt: 'currentdir', 'Current service location (can be file or directory path)', args: 1, required: true)
            resultformat(longOpt: 'resultformat', 'The output format ', args: 1, required: true)
            resultlocation(longOpt: 'resultlocation', 'The output location', args: 1, required: true)
        }

        println "\n\nWelcome to CAS Service Converter!"
        def opt = cli.parse(args)

        if (!opt) return
        if (opt.help) cli.usage()

        def script = new CASServiceConverter(opt)
        script.run()

        println "Done"
    }
}
