(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('reportObservables', function(UtilsSrv) {
        return {
            restrict: 'E',
            scope: {
                'observables': '='
            },
            templateUrl: 'views/directives/report-observables.html',
            link: function(scope) {
                scope.$watch('observables', function() {
                    scope.groups = _.groupBy(scope.observables, 'dataType');
                    scope.pagination = {
                        pageSize: 10,
                        currentPage: 1,
                        filter: '',
                        data: scope.observables
                    };
                })
            },
            controller: function($scope) {
                $scope.filterArtifacts = function(type) {
                    $scope.pagination.filter = type;
                    $scope.pagination.currentPage = 1;

                    if(type !== '') {
                        $scope.pagination.data = $scope.groups[type];
                    } else {
                        $scope.pagination.data = $scope.observables;
                    }
                }

                $scope.selectedObservables = function() {
                    return _.filter($scope.observables, function(item) {
                        return item.selected;
                    });
                }

                $scope.selectAll = function(type) {
                    // TODO
                }
            }
        };
    });

})();
