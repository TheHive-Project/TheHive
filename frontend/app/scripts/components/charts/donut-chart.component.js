(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('donutChart', {
            controller: function($scope) {
                var self = this;

                this.$onInit = function() {
                    $scope.$watch('$cmp.data', function (data) {
                        self.updateChart(data);
                    });
                };

                this.updateChart = function(rawData) {
                    this.error = false;

                    var data = _.map(rawData, function(item) {
                        return [item.key, item.count];
                    });

                    this.chart = {
                        data: {
                            columns: data,
                            names: self.labels || undefined,
                            type: 'donut',
                            onclick: function(d) {
                                if(self.onItemClicked) {
                                    self.onItemClicked({
                                        value: d.id
                                    });
                                }
                            }
                        },
                        donut: {
                            label: {
                                show: false
                            }
                        },
                        legend: {
                            position: 'right'
                        }
                    };
                };
            },
            controllerAs: '$cmp',
            template: '<c3 chart="$cmp.chart" error="$cmp.error" height="150" hide-actions="true"></c3>',
            bindings: {
                data: '<',
                labels: '<',
                title: '@',
                field: '@',
                onItemClicked: '&'
            }
        });
})();
