(function() {
    'use strict';
    angular.module('theHiveComponents')
        .component('statsItem', {
            controller: function() {
                var self = this;

                this.onClick = function(value) {
                    self.onItemClicked({
                        field: self.field,
                        value: self.values ? self.values[value] : value
                    });
                };
            },
            controllerAs: '$cmp',
            templateUrl: 'views/components/list/stats-item.component.html',
            replace: true,
            bindings: {
                field: '@',
                title: '@',
                data: '<',
                mode: '<',
                labels: '<',
                values: '<',
                onItemClicked: '&'
            }
        });
})();
