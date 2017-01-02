/**
 * Controller for statistics page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('StatisticsCtrl', function($scope, $rootScope, $timeout, StatisticSrv) {
        $scope.globalFilters = StatisticSrv.getFilters() || {
            fromDate: moment().subtract(30, 'd').toDate(),
            toDate: moment().toDate(),
            tags: []
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

        // Prepare the global query
        $scope.prepareGlobalQuery = function() {
            // Handle date queries
            var start = $scope.globalFilters.fromDate ? $scope.globalFilters.fromDate.getTime() : '*';
            var end = $scope.globalFilters.toDate ? $scope.globalFilters.toDate.setHours(23,59,59,999) : '*';

            // Handle date queries
            var tags = _.map($scope.globalFilters.tags, function(tag) {
                return tag.text;
            });

            return function(options) {
                return {
                    _and: [
                        {
                            _between: { _field: options.dateField, _from: start, _to: end}
                        },
                        {
                            _or: _.map(tags, function(t) {
                                return { _field: options.tagsField, _value: t };
                            })
                        }
                    ]
                };
            };

            //return segments.join(' AND ');

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
