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
        ];

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
            id: 'avg',
            label: 'avg'
        }, {
            id: 'min',
            label: 'min'
        }, {
            id: 'max',
            label: 'max'
        }];

        this.serieTypes = ['line', 'area', 'spline', 'area-spline', 'bar'];

        this.typeClasses = {
            container: 'fa-window-maximize',
            bar: 'fa-bar-chart',
            donut: 'fa-pie-chart',
            line: 'fa-line-chart',
            multiline: 'fa-area-chart',
            counter: 'fa-calculator',
            text: 'fa-file'
        };

        this.sortOptions = [{
            name: '+_count',
            label: 'Ascendant (Smaller first)'
        }, {
            name: '-_count',
            label: 'Descendant (Bigger first)'
        }];

        this.colorsPattern = [
            '#0675a4', '#f46c54', '#fbcd35', '#3cbcb4', '#305868', '#a42414', '#25c4f1', '#ac7b1d', '#ecec24', '#8cc47c', '#a1a4ac', '#043444', '#ad8d8d'
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
                type: 'multiline',
                options: {
                    title: null,
                    entity: null
                }
            },
            {
                type: 'text',
                options: {
                    title: null,
                    template: null,
                    entity: null
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

        this.skipFields = function(fields, types) {
            return _.filter(fields, function(item) {
                return types.indexOf(item.type) === -1;
            });
        };

        this.pickFields = function(fields, types) {
            return _.filter(fields, function(item) {
                return types.indexOf(item.type) !== -1;
            });
        };

        this.fieldsForAggregation = function(fields, agg) {
            if(agg === 'count') {
                return [];
            } else if(agg === 'sum' || agg === 'avg') {
                return self.pickFields(fields, ['number']);
            } else {
                return fields;
            }
        };

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

        this.hasMinimalConfiguration = function(component) {
            switch (component.type) {
                case 'multiline':
                case 'text':
                    return component.options.series.length === _.without(_.pluck(component.options.series, 'entity'), undefined).length;
                default:
                    return !!component.options.entity;
            }
        };

        this.buildFiltersQuery = function(fields, filters) {
            return QueryBuilderSrv.buildFiltersQuery(fields, filters);
        };

        this.buildChartQuery = function(filter, query) {
            var criteria = _.filter(_.without([filter, query], null, undefined, '', '*'), function(c){return !_.isEmpty(c);});

            if(criteria.length === 0) {
                return {};
            } else {
                return criteria.length === 1 ? criteria[0] : { _and: criteria };
            }
        };

        this.buildPeriodQuery = function(period, field, start, end) {
            if(!period && !start && !end) {
                return null;
            }

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
                from = start && start !== null ? moment(start).valueOf() : null;
                to = end && end !== null ? moment(end).hours(23).minutes(59).seconds(59).milliseconds(999).valueOf() : null;

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
            };
        };

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
        };
    });
})();
