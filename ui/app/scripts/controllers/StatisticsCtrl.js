/**
 * Controller for statistics page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('StatisticsCtrl', function($scope, $rootScope, $timeout, StatisticSrv) {
        $scope.globalFilters = StatisticSrv.getFilters() || {
            fromDate: moment().subtract(30, 'd').toDate(),
            toDate: moment().toDate(),
            tags: [],
            tagsAggregator: 'any'
        };

        $scope.tagsAggregators = {
            any: 'Any of',
            all: 'All of',
            none: 'None of'
        };

        $scope.caseByTlp = {
            title: 'Cases by TLP',
            type: 'case',
            field: 'tlp',
            dateField: 'startDate',
            tagsField: 'tags',
            colors: {
                '0': '#cccccc',
                '1': '#5cb85c',
                '2': '#f0ad4e',
                '3': '#d9534f'
            },
            names: {
                '0': 'White',
                '1': 'Green',
                '2': 'Amber',
                '3': 'Red'
            }
        };

        $scope.caseBySeverity = {
            title: 'Cases by Severity',
            type: 'case',
            field: 'severity',
            dateField: 'startDate',
            tagsField: 'tags',
            colors: {
                '1': '#5bc0de',
                '2': '#f0ad4e',
                '3': '#d9534f'
            },
            names: {
                '1': 'Low',
                '2': 'Medium',
                '3': 'High'
            }
        };

        $scope.caseByStatus = {
            title: 'Cases by status',
            type: 'case',
            field: 'status',
            dateField: 'startDate',
            tagsField: 'tags'
        };

        $scope.caseByResolution = {
            title: 'Resolved cases by resolution',
            type: 'case',
            field: 'resolutionStatus',
            dateField: 'startDate',
            tagsField: 'tags',
            filter: {status: 'Resolved'}
        };

        $scope.caseOverTime = {
            title: 'Cases over time',
            type: 'case',
            fields: ['startDate', 'endDate'],
            dateField: 'startDate',
            tagsField: 'tags',
            names: {
                startDate: 'Number of open cases',
                endDate: 'Number of resolved cases'
            },
            types: {
                startDate: 'bar'
            }
        };

        $scope.caseHandlingDurationOverTime = {
            title: 'Cases handling over time',
            dateField: 'startDate',
            tagsField: 'tags',
            names: {
                max: 'Max',
                min: 'Min',
                avg: 'Avg',
                count: 'Number of resolved cases'
            },
            types: {
                count: 'bar'
            },
            axes: {
                count: 'y2'
            },
            colors: {
                'count': '#ff7f0e',
                'max': '#d62728',
                'min': '#2ca02c',
                'avg': '#1f77b4'
            },
            filter: {status: 'Resolved'}
        };

        $scope.caseMetricsOverTime = {
            title: 'Case metrics over time',
            entity: 'case',
            type: 'line',
            field: 'startDate',
            dateField: 'startDate',
            tagsField: 'tags',
            aggregations: ['sum']
        };

        $scope.observableByDataType = {
            title: 'Observables by Type',
            type: 'case_artifact',
            field: 'dataType',
            dateField: 'startDate',
            tagsField: 'tags',
            filter: {status: 'Ok'}
        };

        $scope.observableByIoc = {
            title: 'Observables by IOC flag',
            type: 'case_artifact',
            field: 'ioc',
            dateField: 'startDate',
            tagsField: 'tags',
            names: {
                'false': 'NOT IOC',
                'true': 'IOC'
            },
            filter: {status: 'Ok'}
        };

        $scope.observableOverTime = {
            title: 'Observables over time',
            type: 'case/artifact',
            fields: ['startDate'],
            dateField: 'startDate',
            tagsField: 'tags',
            names: {
                startDate: 'Number of observables'
            },
            types: {
                startDate: 'bar'
            },
            filter: {status: 'Ok'}
        };

        $scope.setTagsAggregator = function(aggregator) {
            $scope.globalFilters.tagsAggregator = aggregator;
        };


        // Prepare the global query
        $scope.prepareGlobalQuery = function() {
            // Handle date queries
            var start = $scope.globalFilters.fromDate ? $scope.globalFilters.fromDate.getTime() : '*';
            var end = $scope.globalFilters.toDate ? $scope.globalFilters.toDate.setHours(23,59,59,999) : '*';

            // Handle tags query
            var tags = _.map($scope.globalFilters.tags, function(tag) {
                return tag.text;
            });

            return function(options) {
                var queryCriteria = {
                    _and: [
                        {
                            _between: { _field: options.dateField, _from: start, _to: end}
                        }
                    ]
                };

                // Adding tags criteria
                if(tags.length > 0) {
                    var tagsCriterions = _.map(tags, function(t) {
                        return { _field: options.tagsField, _value: t };
                    });
                    var tagsCriteria = {};
                    switch($scope.globalFilters.tagsAggregator) {
                        case 'all':
                            tagsCriteria = {
                                _and: tagsCriterions
                            };
                            break;
                        case 'none':
                            tagsCriteria = {
                                _not: {
                                    _or: tagsCriterions
                                }
                            };
                            break;
                        case 'any':
                        default:
                            tagsCriteria = {
                                _or: tagsCriterions
                            }
                    }
                    queryCriteria._and.push(tagsCriteria);
                }

                return queryCriteria;
            };
        };

        $scope.filter = function() {
            StatisticSrv.setFilters($scope.globalFilters);

            $rootScope.$broadcast('refresh-charts', $scope.prepareGlobalQuery());
        };

        $timeout(function(){
            $scope.filter();
        }, 500);


    });

})();
