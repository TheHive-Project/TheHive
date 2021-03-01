(function() {
    'use strict';

    angular.module('theHiveFilters').filter('filterValue', function(UtilsSrv, UiSettingsSrv) {
        return function(value) {
            var dateFormat = UiSettingsSrv.defaultDateFormat();

            if (angular.isArray(value)) {
                return _.map(value, function(item) {
                    return item.label || item.text;
                }).join(', ');
            } else if(angular.isObject(value) && value.from !== undefined && value.to !== undefined) {
                var result = [];

                result.push({
                    custom: 'Custom',
                    today: 'Today',
                    last7days: 'Last 7 days',
                    last30days: 'Last 30 days',
                    last3months: 'Last 3 months',
                    last6months: 'Last 6 months',
                    lastyear: 'Last year'
                }[value.operator] || 'Custom');

                var start, end;

                if(value.operator && value.operator !== 'custom') {
                    var dateRange = UtilsSrv.getDateRange(value.operator);

                    start = dateRange.from;
                    end = dateRange.to;
                } else {
                    start = value.from;
                    end = value.to;
                }

                if(start !== null) {
                    result.push('From: ' + moment(start).hour(0).minutes(0).seconds(0).format(dateFormat));
                }

                if(end !== null) {
                    result.push('To: ' + moment(end).hour(23).minutes(59).seconds(59).format(dateFormat));
                }

                return result.join(', ');
            } else if(angular.isObject(value) && value.list !== undefined) {
                return _.map(value.list, function(item) {
                    return item.label || item.text;
                }).join(', ');
            } else if(angular.isObject(value) && value.operator !== undefined) {
                return [value.operator, value.value].join(' ');
            }

            return value !== undefined && value !== null ? value : 'Any';
        };
    });

})();
