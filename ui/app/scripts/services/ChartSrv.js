(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('ChartSrv', function() {

            return {
                timeIntervals: [{
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
                }],

                aggregations: [{
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
                }]
            };
        });
})();
