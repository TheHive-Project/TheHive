(function() {
    'use strict';
    angular.module('theHiveServices').service('DashboardSrv', function(QueryBuilderSrv, localStorageService, $q, AuthenticationSrv, $http) {
        var baseUrl = './api/dashboard';
        var self = this;

        this.metadata = null;

        this.defaultDashboard = {
            period: 'all',
            items: [
                {
                    type: 'container',
                    items: []
                }
            ]
        };

        this.dashboardPeriods = [
            {
                type: 'all',
                label: 'All time'
            },
            {
                type: 'last3Months',
                label: 'Last 3 months'
            },
            {
                type: 'last30Days',
                label: 'Last 30 days'
            },
            {
                type: 'last7Days',
                label: 'Last 7 days'
            }
        ]

        this.timeIntervals = [{
            code: '1d',
            label: 'By day'
        }, {
            code: '1w',
            label: 'By week'
        }, {
            code: '1M',
            label: 'By month'
        }, {
            code: '1y',
            label: 'By year'
        }];

        this.aggregations = [{
            id: 'count',
            label: 'count'
        }, {
            id: 'sum',
            label: 'sum'
        }, {
            id: 'min',
            label: 'min'
        }, {
            id: 'max',
            label: 'max'
        }, {
            id: 'avg',
            label: 'avg'
        }];

        this.serieTypes = ['line', 'area', 'spline', 'area-spline', 'bar'];

        this.typeClasses = {
            container: 'fa-window-maximize',
            bar: 'fa-bar-chart',
            donut: 'fa-pie-chart',
            line: 'fa-line-chart',
            counter: 'fa-calculator'
        };

        this.colorsPattern = [
            '#0675a4', '#f46c54', '#043444' ,'#305868', '#fbcd35', '#3cbcb4', '#a42414', '#25c4f1', '#ac7b1d', '#ecec24', '#8cc47c', '#a1a4ac', '#ad8d8d'
        ];

        this.toolbox = [
            {
                type: 'container',
                items: []
            },
            {
                type: 'bar',
                options: {}
            },
            {
                type: 'line',
                options: {
                    title: null,
                    entity: null,
                    field: null
                }
            },
            {
                type: 'donut',
                options: {
                    title: null,
                    entity: null,
                    field: null
                }
            },
            {
                type: 'counter',
                options: {
                    title: null
                }
            }
        ];

        this.renderers = {
            severity: function() {}
        };

        this.create = function(dashboard) {
            return $http.post(baseUrl, dashboard);
        };

        this.update = function(id, dashboard) {
            var db = _.pick(dashboard, 'id', 'title', 'description', 'status', 'definition');

            return $http.patch(baseUrl + '/' + id, db);
        };

        this.list = function() {
            return $http.post(baseUrl + '/_search', {
                range: 'all',
                sort: ['-status', '-updatedAt', '-createdAt'],
                query: {
                    _and: [
                        {
                            _not: { status: 'Deleted' }
                        },
                        {
                            _or: [{ status: 'Shared' }, { createdBy: AuthenticationSrv.currentUser.id }]
                        }
                    ]
                }
            });
        };

        this.get = function(id) {
            return $http.get(baseUrl + '/' + id);
        };

        this.remove = function(id) {
            return $http.delete(baseUrl + '/' + id);
        };

        this._objectifyBy = function(collection, field) {
            var obj = {};

            _.each(collection, function(item) {
                obj[item[field]] = item;
            });

            return obj;
        };

        this.getMetadata = function() {
            var defer = $q.defer();

            if (this.metadata !== null) {
                defer.resolve(this.metadata);
            } else {
                $http
                    .get('./api/describe/_all')
                    .then(function(response) {
                        var data = response.data;
                        var metadata = {
                            entities: _.keys(data).sort()
                        };

                        _.each(metadata.entities, function(entity) {
                            metadata[entity] = _.omit(data[entity], 'attributes');
                            metadata[entity].attributes = self._objectifyBy(data[entity].attributes, 'name');
                        });

                        self.metadata = metadata;

                        defer.resolve(metadata);
                    })
                    .catch(function(err) {
                        defer.reject(err);
                    });
            }

            return defer.promise;
        };

        this.buildFiltersQuery = function(fields, filters) {
            return QueryBuilderSrv.buildFiltersQuery(fields, filters);
        };

        this.buildChartQuery = function(filter, query) {
            var criteria = _.without([filter, query], null, undefined, '', '*');

            return criteria.length === 1 ? criteria[0] : { _and: criteria };
        }

        this.buildPeriodQuery = function(period, field, start, end) {
            var today = moment().hours(0).minutes(0).seconds(0).milliseconds(0),
                from,
                to = moment(today).hours(23).minutes(59).seconds(59).milliseconds(999);

            if (period === 'last7Days') {
                from = moment(today).subtract(7, 'days');
            } else if (period === 'last30Days') {
                from = moment(today).subtract(30, 'days');
            } else if (period === 'last3Months') {
                from = moment(today).subtract(3, 'months');
            } else if(period === 'custom') {
                from = start && start != null ? moment(start).valueOf() : null;
                to = end && end != null ? moment(end).hours(23).minutes(59).seconds(59).milliseconds(999).valueOf() : null;

                if (from !== null && to !== null) {
                    return {
                        _between: { _field: field, _from: from, _to: to }
                    };
                } else if (from !== null) {
                    return {
                        _gt: { _field: field, _value: from }
                    };
                } else {
                    return {
                        _lt: { _field: field, _value: to }
                    };
                }
            }

            return period === 'all' ? null : {
                _between: { _field: field, _from: from.valueOf(), _to: to.valueOf() }
            }

            return null;
        }

        this.exportDashboard = function(dashboard) {
            var fileName = dashboard.title.replace(/\s/gi, '_') + '.json';
            var content = _.omit(dashboard,
                '_type',
                'id',
                'createdAt',
                'updatedAt',
                'createdBy',
                'updatedBy');

            content.definition = JSON.parse(content.definition);

            // Create a blob of the data
            var fileToSave = new Blob([JSON.stringify(content)], {
                type: 'application/json',
                name: fileName
            });

            // Save the file
            saveAs(fileToSave, fileName);
        }
    });
})();
