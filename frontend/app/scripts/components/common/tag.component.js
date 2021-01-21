(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('tag', {
            controller: function() {
                this.$onInit = function() {
                    this.tag = _.without([
                        this.value.namespace,
                        ':',
                        this.value.predicate,
                        this.value.value ? ("=\"" + this.value.value + "\"") : null
                    ], null).join('');
                };
            },
            controllerAs: '$ctrl',
            templateUrl: 'views/components/common/tag.component.html',
            bindings: {
                value: '<'
            }
        });
})();
