(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('taskFlags', {
            controller: function() {
                this.filterBy = function(fieldName, newValue) {
                    this.onFilter({
                        fieldName: fieldName,
                        value: newValue
                    });
                };
            },
            controllerAs: '$ctrl',
            templateUrl: 'views/components/common/task-flags.component.html',
            bindings: {
                task: '<',
                inline: '<',
                onFilter: '&'
            }
        });
})();
