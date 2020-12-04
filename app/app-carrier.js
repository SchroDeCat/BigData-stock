'use strict';
const http = require('http');
var assert = require('assert');
const express= require('express');
const app = express();
const mustache = require('mustache');
const filesystem = require('fs');
const url = require('url');
const port = Number(process.argv[2]);

const hbase = require('hbase')
var hclient = hbase({ host: process.argv[3], port: Number(process.argv[4])})

function rowToMap(row) {
    var stats = {}
    row.forEach(function (item) {
        stats[item['column']] = Number(item['$'])
    });
    return stats;
}

hclient.table('zhangfx_delay_by_carrier').row('AA2019').get((error, value) => {
    console.info(rowToMap(value))
    console.info(value)
})


app.use(express.static('public'));
app.get('/delays.html',function (req, res) {
    const carrier_year=req.query['carrier'] + req.query['year'];
    console.log(carrier_year);
    hclient.table('zhangfx_delay_by_carrier').row(carrier_year).get(function (err, cells) {
        const carrierInfo = rowToMap(cells);
        console.log(carrierInfo)
        function weather_on_time(weather) {
            const flights = carrierInfo["delay:" + weather + "_flights"];
            const on_time = flights - carrierInfo["delay:" + weather + "_delays"];
            if(flights == 0)
                return " - ";
            return (on_time/flights).toFixed(3);
        }

        var template = filesystem.readFileSync("result_carrier.mustache").toString();
        var html = mustache.render(template,  {
            carrier : req.query['carrier'],
            year : req.query['year'],
            total_ont: weather_on_time("total"),
            clear_ont : weather_on_time("clear"),
            fog_ont : weather_on_time("fog"),
            rain_ont : weather_on_time("rain"),
            snow_ont : weather_on_time("snow"),
            hail_ont : weather_on_time("hail"),
            thunder_ont : weather_on_time("thunder"),
            tornado_ont : weather_on_time("tornado")
        });
        res.send(html);
    });
});

app.listen(port);
