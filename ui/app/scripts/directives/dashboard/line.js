(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dashboardLine', function($http, $state, DashboardSrv, NotificationSrv) {
        return {
            restrict: 'E',
            scope: {
                filter: '=?',
                options: '=',
                entity: '=',
                autoload: '=',
                mode: '=',
                refreshOn: '@',
                resizeOn: '@',
                metadata: '='
            },
            template: '<c3 chart="chart" resize-on="{{resizeOn}}" error="error" on-save-csv="getCsv()"></c3>',
            link: function(scope) {
                scope.error = false;
                scope.chart = {};

                scope.intervals = DashboardSrv.timeIntervals;
                scope.interval = scope.intervals[2];

                scope.load = function() {
                    if(!scope.entity) {
                        scope.error = true;
                        return;
                    }

                    var query = DashboardSrv.buildChartQuery(scope.filter, scope.options.query);

                    var statsPromise = $http.post('./api' + scope.entity.path + '/_stats', {
                        query: query,
                        stats: [{
                            _agg: 'time',
                            _fields: [scope.options.field],
                            _interval: scope.options.interval || scope.interval.code,
                            _select: _.map(scope.options.series || [], function(serie, index) {
                                var s = {
                                    _agg: serie.agg,
                                    _name: 'agg_' + (index + 1),
                                    _query: serie.query || {}
                                };

                                if(serie.agg !== 'count') {
                                    s._field = serie.field;
                                }

                                return s;
                            })
                        }]
                    });

                    statsPromise.then(function(response) {
                        scope.error = false;
                        var labels = _.keys(response.data).map(function(d) {
                            return moment(d * 1).format('YYYY-MM-DD');
                        });
                        var len = labels.length,
                            data = {_date: (new Array(len)).fill(0)},
                            rawData = {};

                        _.each(response.data, function(value, key) {
                            rawData[key] = value[scope.options.field]
                        });

                        _.each(rawData, function(value) {
                            _.each(_.keys(value), function(key){
                                data[key] = (new Array(len)).fill(0);
                            });
                        });

                        var i = 0;
                        var orderedDates = _.sortBy(_.keys(rawData));

                        _.each(orderedDates, function(key) {
                            var value = rawData[key];
                            data._date[i] = moment(key * 1).format('YYYY-MM-DD');

                            _.each(_.keys(value), function(item) {
                                data[item][i] = value[item];
                            });

                            i++;
                        });

                        scope.types = {};
                        scope.names = {};
                        scope.axes = {};
                        scope.colors = {};

                        _.each(scope.options.series, function(serie, index) {
                            var key = serie.field,
                                agg = serie.agg,
                                dataKey = agg === 'count' ? 'count' : (agg + '_' + key),
                                columnKey = 'agg_' + (index + 1);

                            scope.types[columnKey] = serie.type || 'line';
                            scope.names[columnKey] = serie.label || (agg === 'count' ? 'count' : (agg + ' of ' + key));
                            scope.axes[columnKey] = (scope.types[columnKey] === 'bar') ? 'y2' : 'y';
                            scope.colors[columnKey] = serie.color;
                        });

                        // Compute stack groups
                        var groups = {};
                        _.each(scope.types, function(value, key) {
                            if (groups[value]) {
                                groups[value].push(key);
                            } else {
                                groups[value] = [key];
                            }
                        });
                        scope.groups = scope.options.stacked === true ? _.values(groups) : {};

                        scope.data = data;

                        console.log('Line data:', scope.data);

                        var chart = {
                            data: {
                                x: '_date',
                                json: scope.data,
                                names: scope.names || {},
                                type: scope.type || 'bar',
                                types: scope.types || {},
                                axes: scope.axes || {},
                                colors: scope.colors || {},
                                groups: scope.groups || []
                            },
                            bar: {
                                width: {
                                    ratio: 1 - Math.exp(-len/20)
                                }
                            },
                            axis: {
                                x: {
                                    type: 'timeseries',
                                    tick: {
                                        format: '%Y-%m-%d',
                                        rotate: 90,
                                        height: 50
                                    }
                                },
                                y2: {
                                    show: _.values(scope.axes).indexOf('y2') !== -1
                                }
                            },
                            zoom: {
                                enabled: scope.options.zoom || false
                            }
                        };

                        scope.chart = chart;
                    }, function(err) {
                        scope.error = true;
                        NotificationSrv.log('Failed to fetch data, please edit the widget definition', 'error');
                    });
                };

                scope.getCsv = function() {
                    var dates = scope.data._date;
                    var keys = _.keys(scope.data);

                    // TODO update headers
                    var csv = [{data: _.map(keys, function(key){
                        return scope.names[key] || key;
                    }).join(';')}];

                    var row = [];
                    for(var i=0; i<dates.length; i++) {
                        row = _.map(keys, function(key) {
                            return scope.data[key][i];
                        });

                        csv.push({data: row.join(';')});
                    }

                    return csv;
                };

                if (scope.autoload === true) {
                    scope.load();
                }

                if (!_.isEmpty(scope.refreshOn)) {
                    scope.$on(scope.refreshOn, function(event, filter) {
                        scope.filter = filter;
                        scope.load();
                    });
                }
            }
        };
    });
})();
