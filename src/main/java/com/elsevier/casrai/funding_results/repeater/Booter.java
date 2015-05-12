package com.elsevier.casrai.funding_results.repeater;

import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerFilter;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * A booter for the casrai repeater
 */
public class Booter {
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(Booter.class);

	@Option(name="-port", required = false, usage = "the WS port for this repeater")
	private int port = 8880;

	@Option(name="-db", required = false, usage = "name of the MongoDB database this repeater uses for storing data (default: casrai-repeater)")
	private String db = "casrai-repeater";

	@Option(name="-col", required = false, usage = "name of the MongoDB collection this repeater uses for storing data (default: data)")
	private String col = "data";

	@Option(name="-filter", required = false, usage = "filters harvested data to include only awards with even or odd amounts (default: none) (options: even, odd)")
	private String filter = null;

	@Argument
	private List<String> harvestUrls = Lists.newArrayList();


	public static void main(String[] args) throws Exception {
		final Booter booter = new Booter();

		final CmdLineParser parser = new CmdLineParser(booter);
		try {
			parser.parseArgument(args);
			if(booter.harvestUrls.isEmpty() )
				throw new CmdLineException(parser,"No harvest URLs given");
		} catch( CmdLineException e ) {
			System.err.println(e.getMessage());
			System.err.println("java " + Booter.class.getName() + " [options...] <harvest urls>...");
			parser.printUsage(System.err);
			System.err.println();

			System.err.println("  Example: java " + Booter.class.getName() + " " + parser.printExample(OptionHandlerFilter.ALL) + " http://localhost:9998/");

			return;
		}

		booter.execute();
	}

	private void execute() {
		// Storage
		log.info("Initializing storage...");
		final CasraiStorageImpl storage = new CasraiStorageImpl();
		storage.setDb(db);
		storage.setCol(col);
		storage.initialize();

		// Boot harvester
		log.info("Starting harvester...");
		final CasraiHarvester harvester = new CasraiHarvester();
		harvester.setHarvestUrls(harvestUrls);
		harvester.setStorage(storage);
		harvester.setFilter(filter);
		harvester.start();

		// Boot WS
		log.info("Starting ws on port {}...", port);
		final CasraiWS ws = new CasraiWS();
		ws.setPort(port);
		ws.setStorage(storage);
		ws.setFilter(filter);
		ws.start();
	}

}
