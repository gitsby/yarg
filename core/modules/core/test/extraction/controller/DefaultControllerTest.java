/*
 * Copyright 2013 Haulmont
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package extraction.controller;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.haulmont.yarg.loaders.factory.DefaultLoaderFactory;
import com.haulmont.yarg.loaders.impl.GroovyDataLoader;
import com.haulmont.yarg.loaders.impl.JsonDataLoader;
import com.haulmont.yarg.loaders.impl.SqlDataLoader;
import com.haulmont.yarg.reporting.DataExtractorImpl;
import com.haulmont.yarg.reporting.extraction.DefaultExtractionContextFactory;
import com.haulmont.yarg.reporting.extraction.DefaultExtractionControllerFactory;
import com.haulmont.yarg.reporting.extraction.ExtractionContextFactory;
import com.haulmont.yarg.structure.BandData;
import com.haulmont.yarg.structure.Report;
import com.haulmont.yarg.structure.ReportBand;
import com.haulmont.yarg.structure.impl.BandBuilder;
import com.haulmont.yarg.structure.impl.ReportBuilder;
import com.haulmont.yarg.util.groovy.DefaultScriptingImpl;
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import utils.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static utils.ExtractionUtils.checkHeader;
import static utils.ExtractionUtils.checkMasterData;

public class DefaultControllerTest {

    static TestDatabase database = new TestDatabase();
    static DefaultLoaderFactory loaderFactory = new DefaultLoaderFactory();
    DefaultExtractionControllerFactory controllerFactory
            = new DefaultExtractionControllerFactory(loaderFactory);

    ExtractionContextFactory contextFactory = new DefaultExtractionContextFactory(ExtractionUtils.emptyExtractor());

    @BeforeClass
    public static void construct() throws Exception {
        database.setUpDatabase();
        FixtureUtils.loadDb(database.getDs(), "extraction/fixture/controller_test.sql");

        loaderFactory.setSqlDataLoader(new SqlDataLoader(database.getDs()));
        loaderFactory.setGroovyDataLoader(new GroovyDataLoader(new DefaultScriptingImpl()));
        loaderFactory.setJsonDataLoader(new JsonDataLoader());
    }

    @AfterClass
    public static void destroy() throws Exception {
        database.stop();
    }

    @Test
    public void testSqlExtractionForCrosstabBand() throws IOException, URISyntaxException {
        ReportBand band = YmlDataUtil.bandFrom(FileLoader.load("extraction/fixture/default_sql_report_band.yml"));
        BandData rootBand = new BandData(BandData.ROOT_BAND_NAME);
        rootBand.setData(new HashMap<>());
        rootBand.setFirstLevelBandDefinitionNames(new HashSet<>());

        Multimap<String, BandData> reportBandMap = HashMultimap.create();

        for (ReportBand definition : band.getChildren()) {
            List<BandData> data = controllerFactory.controllerBy(definition.getBandOrientation())
                    .extract(contextFactory.context(definition, rootBand, new HashMap<>()));

            Assert.assertNotNull(data);

            data.forEach(b-> {
                Assert.assertNotNull(b);
                Assert.assertTrue(StringUtils.isNotEmpty(b.getName()));

                reportBandMap.put(b.getName(), b);
            });

            rootBand.addChildren(data);
            rootBand.getFirstLevelBandDefinitionNames().add(definition.getName());
        }

        checkHeader(reportBandMap.get("header"), 12, "MONTH_NAME", "MONTH_ID");
        checkMasterData(reportBandMap.get("master_data"), 3, 12,
                "USER_ID", "LOGIN", "HOURS");
    }

    @Test
    public void testGroovyExtractionForBand() throws IOException, URISyntaxException {
        ReportBand band = YmlDataUtil.bandFrom(FileLoader.load("extraction/fixture/default_groovy_report_band.yml"));

        BandData rootBand = new BandData(BandData.ROOT_BAND_NAME);
        rootBand.setData(new HashMap<>());
        rootBand.setFirstLevelBandDefinitionNames(new HashSet<>());

        Multimap<String, BandData> reportBandMap = HashMultimap.create();

        for (ReportBand definition : band.getChildren()) {
            List<BandData> data = controllerFactory.controllerBy(definition.getBandOrientation())
                    .extract(contextFactory.context(definition, rootBand, new HashMap<>()));

            Assert.assertNotNull(data);

            data.forEach(b-> {
                Assert.assertNotNull(b);
                Assert.assertTrue(StringUtils.isNotEmpty(b.getName()));

                reportBandMap.put(b.getName(), b);
            });

            rootBand.addChildren(data);
            rootBand.getFirstLevelBandDefinitionNames().add(definition.getName());
        }

        checkHeader(reportBandMap.get("header"), 2, "name", "id");
        checkMasterData(reportBandMap.get("master_data"), 2, 2,
                "id", "name", "value", "user_id");
    }

    @Test
    public void testJsonExtractionForBand() throws IOException, URISyntaxException {
        ReportBand band = YmlDataUtil.bandFrom(FileLoader.load("extraction/fixture/default_json_report_band.yml"));

        BandData rootBand = new BandData(BandData.ROOT_BAND_NAME);
        rootBand.setData(new HashMap<>());
        rootBand.setFirstLevelBandDefinitionNames(new HashSet<>());

        Multimap<String, BandData> reportBandMap = HashMultimap.create();

        for (ReportBand definition : band.getChildren()) {
            List<BandData> data = controllerFactory.controllerBy(definition.getBandOrientation())
                    .extract(contextFactory.context(definition, rootBand, ExtractionUtils.getParams(definition)));

            Assert.assertNotNull(data);

            data.forEach(b-> {
                Assert.assertNotNull(b);
                Assert.assertTrue(StringUtils.isNotEmpty(b.getName()));

                reportBandMap.put(b.getName(), b);
            });

            rootBand.addChildren(data);
            rootBand.getFirstLevelBandDefinitionNames().add(definition.getName());
        }

        checkHeader(reportBandMap.get("header"), 2, "name", "id");
        checkMasterData(reportBandMap.get("master_data"), 2, 2,
                "id", "name", "value", "user_id");
    }

    @Test
    public void stressTest() throws IOException, URISyntaxException {
        int queries = 100;
        int recordsPerQuery = 10000;

        Report report = createReport(queries, recordsPerQuery);

        BandData rootBand = new BandData(BandData.ROOT_BAND_NAME);
        rootBand.setData(new HashMap<>());
        rootBand.addReportFieldFormats(report.getReportFieldFormats());
        rootBand.setFirstLevelBandDefinitionNames(new HashSet<>());

        long start = System.currentTimeMillis();
        try {
            new DataExtractorImpl(new DefaultLoaderFactory().setGroovyDataLoader(
                    new GroovyDataLoader(new DefaultScriptingImpl()))).extractData(report, new HashMap<>(), rootBand);
        } finally {
            System.out.println(
                    String.format("Report processing stress test (%d queries and %d records per query) took %d ms",
                            queries, recordsPerQuery, System.currentTimeMillis() - start)
            );
        }
    }

    private Report createReport(int queries, int recordsPerQuery) {
        BandBuilder bandBuilder = new BandBuilder()
                .name("band");
        for (int i = 0; i < queries; i++) {
            String script = "import java.util.*;\n" +
                    "int i = " + i + ";\n" +
                    "List result = new ArrayList<>(" + recordsPerQuery + ");\n" +
                    "for (int j = 0; j < " + recordsPerQuery + "; j++) {\n" +
                    "   Map<String, Object> record = new LinkedHashMap<>();\n" +
                    "   record.put(\"col\" + i + j, Integer.toString(i) + Integer.toString(j));\n" +
                    "   record.put(\"link\", j);\n" +
                    "   result.add(record);\n" +
                    "}\n" +
                    "return result;";
            bandBuilder.query("q" + i, script, "groovy", "link");
        }

        return new ReportBuilder()
                .band(bandBuilder.build())
                .name("report")
                .build();
    }
}
